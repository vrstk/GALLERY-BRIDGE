package com.gallerybridge.app.transfer

import android.content.Context
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.GalleryKeys
import com.gallerybridge.app.core.Media
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
    data class Entry(val media: Media, val state: String, val failureReason: String = "")

    /** Legacy FAILED items have no outcome classification and must be reviewed. */
    internal fun restoreState(state: String, classified: Boolean): String =
        if (state == "FAILED" && !classified) "BLOCKED" else state

    /** Never report a transfer complete unless its durable queue state was saved. */
    class CheckpointFailure(message: String) : IllegalStateException(message)

    private fun storage(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    @Synchronized fun load(context: Context): Snapshot? {
        val raw = storage(context).getString(KEY, null) ?: return null
        return try {
            val data = JSONObject(raw)
            val list = data.getJSONArray("entries")
            val entries = buildList {
                for (i in 0 until list.length()) {
                    val x = list.getJSONObject(i)
                    add(Entry(Media(x.getString("url"), x.getString("name"),
                        Kind.valueOf(x.getString("kind")), x.optString("source"), x.optString("referrer")),
                        restoreState(x.optString("state", "PENDING"), x.has("failureReason")),
                        if (x.optString("state") == "FAILED" && !x.has("failureReason"))
                            "Сбой из предыдущей версии. Проверьте назначение перед повтором."
                        else x.optString("failureReason").take(240)))
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
                .put("state", entry.state).put("failureReason", entry.failureReason.take(240)))
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
    fun applyItemResult(snapshot: Snapshot, entryIndex: Int, media: Media, state: String, reason: String = ""): Snapshot {
        require(state in setOf("DONE", "SKIPPED", "FAILED", "BLOCKED")) { "Invalid queue state: $state" }
        val old = snapshot.entries.getOrNull(entryIndex) ?: return snapshot
        if (old.media != media || old.state in setOf("DONE", "SKIPPED", "BLOCKED")) return snapshot
        val entries = snapshot.entries.toMutableList()
        entries[entryIndex] = old.copy(state = state,
            failureReason = if (state == "FAILED" || state == "BLOCKED")
                reason.replace("\n", " ").take(240) else "")
        return snapshot.copy(entries = entries)
    }

    @Synchronized fun checkpoint(
        context: Context, id: Long, entryIndex: Int, media: Media, state: String, reason: String = ""
    ) {
        val before = load(context)
            ?: throw CheckpointFailure("Сохранённая очередь недоступна; передача остановлена.")
        if (before.id != id) {
            throw CheckpointFailure("Очередь изменилась во время передачи; результат не записан.")
        }
        val after = applyItemResult(before, entryIndex, media, state, reason)
        if (after == before) {
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

    fun newSnapshot(
        model: String, title: String, date: String, target: String,
        root: String, account: String, files: List<Media>, sourceUrl: String = ""
    ): Snapshot = Snapshot(System.currentTimeMillis(), model, title, date, target,
        root, account, files.map { Entry(it, "PENDING") },
        GalleryKeys.canonical(sourceUrl).orEmpty())
}
