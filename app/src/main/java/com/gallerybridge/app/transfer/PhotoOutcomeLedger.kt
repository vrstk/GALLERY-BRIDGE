package com.gallerybridge.app.transfer

import android.content.Context
import com.gallerybridge.app.core.GalleryKeys
import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.core.Paths
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/**
 * Conservative local evidence of prior per-photo results, NOT proof of cloud sync.
 * Stores only SHA-256 fingerprints and DONE/SKIPPED, never photo URLs, names,
 * account names, passwords or unredacted destination identifiers.
 *
 * Legacy queues without a source page cannot be attributed safely.
 */
internal object PhotoOutcomeLedger {
    private const val PREFS = "gallery_bridge_photo_outcomes_v1"
    private const val KEY = "records"
    const val LIMIT = 10000

    data class Record(
        val token: String, val state: String, val at: Long, val contentSha256: String = ""
    )

    internal fun fingerprint(
        sourceUrl: String, target: String, root: String, account: String, media: Media
    ): String? {
        val page = GalleryKeys.canonical(sourceUrl) ?: return null
        if (media.kind != Kind.PHOTO || media.name.isBlank() || target.isBlank() || root.isBlank())
            return null
        val normalizedName = Paths.clean(media.name).lowercase(Locale.ROOT)
        val accountScope = if (target == "Mail WebDAV") account.trim().lowercase(Locale.ROOT) else ""
        val scope = listOf(page, target, root, accountScope, normalizedName).joinToString("\u0000")
        val digest = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    /** Different source URLs with the same destination filename are ambiguous. */
    internal fun duplicateNames(files: List<Media>): Set<String> {
        val seen = HashSet<String>()
        val duplicates = LinkedHashSet<String>()
        files.filter { it.kind == Kind.PHOTO }.forEach { photo ->
            val name = Paths.clean(photo.name).lowercase(Locale.ROOT)
            if (!seen.add(name)) duplicates.add(name)
        }
        return duplicates
    }

    /** Malformed stored data is an error, not an empty history that permits a silent repeat. */
    internal fun decode(raw: String?): List<Record> {
        if (raw == null) return emptyList()
        val json = JSONArray(raw)
        require(json.length() <= LIMIT * 2) { "Журнал фотографий повреждён: превышен лимит." }
        return buildList {
            for (i in 0 until json.length()) {
                val item = json.getJSONObject(i)
                val token = item.getString("token")
                val state = item.getString("state")
                val at = item.getLong("at")
                val digest = item.optString("contentSha256")
                require(token.matches(Regex("[0-9a-f]{64}")) &&
                    state in setOf("DONE", "SKIPPED") && at >= 0 &&
                    (digest.isEmpty() || QueueStore.validContentHash(digest))
                ) { "Журнал фотографий содержит неверную запись." }
                add(Record(token, state, at, digest))
            }
        }.distinctBy { it.token }.take(LIMIT)
    }

    internal fun encode(records: List<Record>): String {
        val json = JSONArray()
        records.take(LIMIT).forEach {
            json.put(JSONObject().put("token", it.token).put("state", it.state)
                .put("at", it.at).put("contentSha256", it.contentSha256))
        }
        return json.toString()
    }

    internal fun fromQueue(queue: QueueStore.Snapshot?, at: Long): List<Record> {
        if (queue == null) return emptyList()
        return queue.entries.mapNotNull { entry ->
            if (entry.state !in setOf("DONE", "SKIPPED")) return@mapNotNull null
            // Batch entries use their own gallery URL. Missing source scope must
            // never be interpreted as permission to repeat earlier transfers.
            fingerprint(entry.galleryUrl.ifBlank { queue.sourceUrl },
                queue.target, queue.root, queue.account, entry.media)
                ?.let { Record(it, entry.state, at,
                    entry.contentSha256.takeIf(QueueStore::validContentHash).orEmpty()) }
        }.distinctBy { it.token }
    }

    internal fun merge(
        previous: List<Record>, queue: QueueStore.Snapshot, at: Long
    ): List<Record> {
        val additions = fromQueue(queue, at)
        if (additions.isEmpty()) return previous
        val previousByToken = previous.associateBy { it.token }
        val enriched = additions.map { record ->
            val known = previousByToken[record.token]
            // No new byte-level evidence: preserve the timestamp of the old digest.
            // Otherwise a later legacy queue could pretend to have reverified its bytes.
            if (record.contentSha256.isEmpty() && known?.contentSha256?.isNotEmpty() == true)
                known
            else record
        }
        return (enriched + previous)
            .distinctBy { it.token }
            .sortedWith(compareByDescending<Record> { it.at }.thenBy { it.token })
            .take(LIMIT)
    }

    internal fun matchedUrls(
        previous: List<Record>,
        sourceUrl: String,
        target: String,
        root: String,
        account: String,
        chosen: List<Media>,
        currentQueue: QueueStore.Snapshot?
    ): Set<String> {
        val known = (previous + fromQueue(currentQueue, 0L)).mapTo(HashSet()) { it.token }
        return chosen.mapNotNull { media ->
            val token = fingerprint(sourceUrl, target, root, account, media)
            media.url.takeIf { token != null && token in known }
        }.toSet()
    }

    @Synchronized fun load(context: Context): List<Record> =
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    /** Synchronous write before the old queue may be removed or replaced. */
    @Synchronized fun remember(context: Context, queue: QueueStore.Snapshot) {
        val before = load(context)
        val after = merge(before, queue, System.currentTimeMillis())
        if (before == after) return
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, encode(after)).commit()) {
            "Не удалось сохранить журнал результатов фотографий; очередь сохранена."
        }
    }

    @Synchronized fun matchedUrls(
        context: Context,
        sourceUrl: String,
        target: String,
        root: String,
        account: String,
        chosen: List<Media>,
        currentQueue: QueueStore.Snapshot?
    ): Set<String> = matchedUrls(load(context), sourceUrl, target, root, account, chosen, currentQueue)
}
