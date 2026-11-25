package com.gallerybridge.app.transfer

import android.content.Context
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.GalleryKeys
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.core.PreparedGallery
import com.gallerybridge.app.core.ModelBatchQueuePlan
import org.json.JSONArray
import org.json.JSONObject

/**
 * One durable queue. Credentials are never persisted.
 * Each item has its own state and is marked DONE/SKIPPED only after the transfer completed.
 */
object QueueStore {
    private const val PREF = "gallery_bridge_queue"
    private const val KEY = "snapshot"

    data class Snapshot(
        val id: Long,
        val model: String,
        val title: String,
        val date: String,
        val target: String,
        val root: String,
        val account: String,
        val entries: List<Entry>,
        val sourceUrl: String = ""
    ) {
        val remaining: Int get() = entries.count { it.state != "DONE" && it.state != "SKIPPED" }
        val completed: Int get() = entries.count { it.state == "DONE" || it.state == "SKIPPED" }
        val retryable: Int get() = entries.count { it.state == "PENDING" || it.state == "FAILED" }
        val blocked: Int get() = entries.count { it.state == "BLOCKED" }
    }
    data class Entry(
        val media: Media,
        val state: String,
        val failureReason: String = "",
        val contentSha256: String = "",
        val galleryUrl: String = "",
        val gallerySourceId: String = "",
        val galleryModel: String = "",
        val galleryTitle: String = "",
        val galleryDate: String = ""
    )

    /** A digest is evidence of downloaded source bytes, not proof of remote synchronization. */
    internal fun validContentHash(value: String): Boolean =
        value.matches(Regex("[0-9a-f]{64}"))

    /** Legacy FAILED items have no outcome classification and must be reviewed. */
    private const val WRITING = "WRITING"
    private const val INTERRUPTED_WRITE =
        "Запись могла прерваться. Проверьте файл в назначении перед повтором."

    internal fun restoreState(state: String, classified: Boolean): String =
        if (state == WRITING || (state == "FAILED" && !classified)) "BLOCKED" else state

    /** Persist this transition before the first destination write, never after it. */
    internal fun markWriting(snapshot: Snapshot, index: Int, media: Media): Snapshot {
        val old = snapshot.entries.getOrNull(index) ?: return snapshot
        if (old.media != media || old.state !in setOf("PENDING", "FAILED")) return snapshot
        val entries = snapshot.entries.toMutableList()
        entries[index] = old.copy(state = WRITING, failureReason = INTERRUPTED_WRITE,
            contentSha256 = "")
        return snapshot.copy(entries = entries)
    }

    @Synchronized fun beginWrite(context: Context, id: Long, index: Int, media: Media) {
        val before = readSnapshot(context, preserveWriting = true)
            ?: throw CheckpointFailure("Очередь недоступна; запись файла отменена.")
        if (before.id != id) throw CheckpointFailure("Очередь изменилась; запись файла отменена.")
        val after = markWriting(before, index, media)
        if (after === before) throw CheckpointFailure("Элемент очереди изменился; запись файла отменена.")
        save(context, after)
    }

    /**
     * Default resume uses all pending and classified failures. An explicit one-file
     * retry is allowed only for a FAILED photo: BLOCKED requires manual review
     * and DONE/SKIPPED must never be replayed.
     * Returns stable indices from the original persisted queue, not a filtered list.
     */
    fun retryIndices(snapshot: Snapshot, singleFailedIndex: Int? = null): List<Int> =
        if (singleFailedIndex == null) {
            snapshot.entries.indices.filter { snapshot.entries[it].state in setOf("PENDING", "FAILED") }
        } else {
            listOf(singleFailedIndex).filter { index ->
                snapshot.entries.getOrNull(index)?.let {
                    it.state == "FAILED" && it.media.kind == Kind.PHOTO
                } == true
            }
        }

    /** Never report a transfer complete unless its durable queue state was saved. */
    class CheckpointFailure(message: String) : IllegalStateException(message)

    private fun storage(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    @Synchronized fun load(context: Context): Snapshot? =
        readSnapshot(context, preserveWriting = false)

    // Only the active writer's checkpoint may read the internal WRITING state.
    // All public readers treat an interrupted or still-unconfirmed write as blocked.
    private fun readSnapshot(context: Context, preserveWriting: Boolean): Snapshot? {
        val raw = storage(context).getString(KEY, null) ?: return null
        return try {
            val data = JSONObject(raw)
            val list = data.getJSONArray("entries")
            val entries = buildList {
                for (i in 0 until list.length()) {
                    val x = list.getJSONObject(i)
                    add(Entry(Media(x.getString("url"), x.getString("name"),
                        Kind.valueOf(x.getString("kind")), x.optString("source"), x.optString("referrer")),
                        if (preserveWriting && x.optString("state") == WRITING) WRITING
                        else restoreState(x.optString("state", "PENDING"), x.has("failureReason")),
                        when {
                            x.optString("state") == WRITING -> INTERRUPTED_WRITE
                            x.optString("state") == "FAILED" && !x.has("failureReason") ->
                                "Сбой из предыдущей версии. Проверьте назначение перед повтором."
                            else -> x.optString("failureReason").take(240)
                        },
                        x.optString("contentSha256").takeIf(::validContentHash).orEmpty(),
                        GalleryKeys.canonical(x.optString("galleryUrl")).orEmpty(),
                        x.optString("gallerySourceId").take(30),
                        x.optString("galleryModel").take(130),
                        x.optString("galleryTitle").take(250),
                        x.optString("galleryDate").take(10)))
                }
            }
            Snapshot(data.getLong("id"), data.getString("model"), data.getString("title"),
                data.optString("date"), data.getString("target"),
                data.getString("root"), data.optString("account", ""), entries,
                GalleryKeys.canonical(data.optString("sourceUrl")).orEmpty())
        } catch (_: Exception) { null }
    }

    @Synchronized fun save(context: Context, snapshot: Snapshot) {
        val json = JSONObject()
            .put("id", snapshot.id)
            .put("model", snapshot.model)
            .put("title", snapshot.title)
            .put("date", snapshot.date)
            .put("target", snapshot.target)
            .put("root", snapshot.root)
            .put("account", snapshot.account)
            .put("sourceUrl", GalleryKeys.canonical(snapshot.sourceUrl).orEmpty())
        val entries = JSONArray()
        for (entry in snapshot.entries) {
            entries.put(JSONObject()
                .put("url", entry.media.url).put("name", entry.media.name)
                .put("kind", entry.media.kind.name)
                .put("source", entry.media.source).put("referrer", entry.media.referrer)
                .put("state", entry.state).put("failureReason", entry.failureReason.take(240))
                .put("contentSha256", entry.contentSha256.takeIf(::validContentHash).orEmpty())
                .put("galleryUrl", entry.galleryUrl).put("gallerySourceId", entry.gallerySourceId)
                .put("galleryModel", entry.galleryModel).put("galleryTitle", entry.galleryTitle)
                .put("galleryDate", entry.galleryDate))
        }
        json.put("entries", entries)
        // Synchronous commit is necessary: .apply() or an unchecked false
        // return may lose DONE/SKIPPED records after a process crash.
        if (!storage(context).edit().putString(KEY, json.toString()).commit()) {
            throw CheckpointFailure("Не удалось сохранить очередь. Передача остановлена, чтобы избежать повторной отправки.")
        }
    }

    /**
     * Only the exact queue entry that was processed may change.
     * URLs can repeat across entries with different names, and even identical
     * media can appear more than once in an imported queue.
     */
    fun applyItemResult(
        snapshot: Snapshot, entryIndex: Int, media: Media, state: String,
        reason: String = "", contentSha256: String = ""
    ): Snapshot {
        require(state in setOf("DONE", "SKIPPED", "FAILED", "BLOCKED")) { "Invalid queue state: $state" }
        val old = snapshot.entries.getOrNull(entryIndex) ?: return snapshot
        if (old.media != media || old.state in setOf("DONE", "SKIPPED", "BLOCKED")) return snapshot
        require(contentSha256.isBlank() || validContentHash(contentSha256)) {
            "Неверный SHA-256 содержимого фотографии."
        }
        val entries = snapshot.entries.toMutableList()
        val safeState = if (old.state == WRITING && state == "FAILED") "BLOCKED" else state
        entries[entryIndex] = old.copy(state = safeState,
            failureReason = if (safeState == "FAILED" || safeState == "BLOCKED")
                reason.ifBlank { if (old.state == WRITING) INTERRUPTED_WRITE else "" }
                    .replace("\n", " ").take(240) else "",
            contentSha256 = if (safeState in setOf("DONE", "SKIPPED")) contentSha256 else "")
        return snapshot.copy(entries = entries)
    }

    @Synchronized fun checkpoint(
        context: Context, id: Long, entryIndex: Int, media: Media, state: String,
        reason: String = "", contentSha256: String = ""
    ) {
        val before = readSnapshot(context, preserveWriting = true)
            ?: throw CheckpointFailure("Сохранённая очередь недоступна; передача остановлена.")
        if (before.id != id) {
            throw CheckpointFailure("Очередь изменилась во время передачи; результат не записан.")
        }
        val after = applyItemResult(before, entryIndex, media, state, reason, contentSha256)
        // Rejected transitions return the original instance. A repeated failure can have identical fields.
        if (after === before) {
            throw CheckpointFailure("Элемент очереди изменился; повторная запись результата заблокирована.")
        }
        save(context, after)
        if (state == "DONE" || state == "SKIPPED") {
            try {
                PhotoOutcomeLedger.remember(context, after)
            } catch (_: Exception) {
                // The queue checkpoint already succeeded, so retrying the file is unsafe.
                throw CheckpointFailure(
                    "Файл обработан, но журнал фотографий не сохранён. Передача остановлена; очередь сохранена."
                )
            }
        }
    }

    /** Only explicit user confirmation can re-enable a file whose write outcome was uncertain. */
    fun allowRetry(snapshot: Snapshot, index: Int, media: Media): Snapshot {
        val old = snapshot.entries.getOrNull(index) ?: return snapshot
        if (old.media != media || old.state != "BLOCKED") return snapshot
        val entries = snapshot.entries.toMutableList()
        entries[index] = old.copy(state = "PENDING", failureReason = "")
        return snapshot.copy(entries = entries)
    }

    @Synchronized fun allowRetryAfterReview(context: Context, id: Long, index: Int, media: Media): Boolean {
        val before = load(context) ?: return false
        if (before.id != id) return false
        val after = allowRetry(before, index, media)
        if (after == before) return false
        save(context, after)
        return true
    }

    /** Clear only the queue explicitly confirmed by the user; never clear a newer one. */
    @Synchronized fun clearIfId(context: Context, id: Long): Boolean {
        val current = load(context) ?: return false
        if (current.id != id) return false
        // Preserve confirmed per-photo outcomes before discarding the only detailed queue.
        PhotoOutcomeLedger.remember(context, current)
        return storage(context).edit().remove(KEY).commit()
    }

    fun foldersFor(entry: Entry, snapshot: Snapshot): List<String> =
        if (entry.galleryUrl.isBlank()) com.gallerybridge.app.core.Paths.folders(
            snapshot.model, snapshot.date, snapshot.title, entry.media.kind)
        else ModelBatchQueuePlan.folder(PreparedGallery(
            entry.galleryUrl, entry.gallerySourceId, entry.galleryModel,
            entry.galleryTitle, entry.galleryDate, listOf(entry.media)), entry.media.kind)

    fun newBatchSnapshot(
        galleries: List<PreparedGallery>, target: String, root: String, account: String
    ): Snapshot {
        val groups = ModelBatchQueuePlan.validate(galleries)
        val entries = groups.flatMap { g ->
            g.media.map { media ->
                Entry(media, "PENDING",
                    galleryUrl = GalleryKeys.canonical(g.sourceUrl).orEmpty(),
                    gallerySourceId = g.sourceId,
                    galleryModel = g.model, galleryTitle = g.title,
                    galleryDate = g.displayedDate)
            }
        }
        return Snapshot(System.currentTimeMillis(), groups.first().model,
            "Пакет: ${groups.size} фотогалерей", "", target, root, account, entries)
    }

    fun newSnapshot(
        model: String, title: String, date: String, target: String,
        root: String, account: String, files: List<Media>, sourceUrl: String = ""
    ): Snapshot = Snapshot(System.currentTimeMillis(), model, title, date, target,
        root, account, files.map { Entry(it, "PENDING") },
        GalleryKeys.canonical(sourceUrl).orEmpty())
}
