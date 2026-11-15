package com.gallerybridge.app.core

import android.content.Context
import com.gallerybridge.app.transfer.QueueStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local, user-controlled catalog of gallery PAGE URLs (never direct media URLs).
 * Existing metadata preferences and historical queues remain untouched.
 */
data class CatalogGallery(
    val url: String,
    val model: String,
    val title: String,
    val date: String,
    val updatedAt: Long,
    val target: String = "",
    val outcome: String = "",
    val completed: Int = 0,
    val total: Int = 0
)

object GalleryCatalogStore {
    private const val PREF = "gallery_bridge_catalog_v1"
    private const val KEY = "galleries"
    internal const val LIMIT = 1500

    /** No guessed URLs. Keep only verified canonical HTTPS gallery links. */
    internal fun decode(raw: String?): List<CatalogGallery> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList<CatalogGallery> {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val url = GalleryKeys.canonical(item.optString("url")) ?: continue
                    val date = item.optString("date").take(10)
                    if (!GalleryKeys.validDate(date)) continue
                    add(CatalogGallery(
                        url = url,
                        model = item.optString("model").take(130),
                        title = item.optString("title").take(250),
                        date = date,
                        updatedAt = item.optLong("updatedAt").coerceAtLeast(0),
                        target = item.optString("target").take(60),
                        outcome = item.optString("outcome").take(80),
                        completed = item.optInt("completed").coerceAtLeast(0),
                        total = item.optInt("total").coerceAtLeast(0)
                    ))
                }
            }.fold(emptyList<CatalogGallery>()) { list, entry -> merge(list, entry) }
        }.getOrDefault(emptyList())
    }

    internal fun encode(entries: List<CatalogGallery>): String {
        val arr = JSONArray()
        entries.take(LIMIT).forEach { entry ->
            arr.put(JSONObject()
                .put("url", entry.url).put("model", entry.model)
                .put("title", entry.title).put("date", entry.date)
                .put("updatedAt", entry.updatedAt).put("target", entry.target)
                .put("outcome", entry.outcome)
                .put("completed", entry.completed).put("total", entry.total))
        }
        return arr.toString()
    }

    /** The most recent label wins; editing a label never loses prior transfer info. */
    internal fun merge(existing: List<CatalogGallery>, incoming: CatalogGallery): List<CatalogGallery> {
        val url = GalleryKeys.canonical(incoming.url) ?: return existing
        if (!GalleryKeys.validDate(incoming.date)) return existing
        val old = existing.firstOrNull { GalleryKeys.same(it.url, url) }
        val hasTransfer = incoming.target.isNotBlank()
        val entry = incoming.copy(
            url = url,
            model = incoming.model.trim().take(130).ifBlank { old?.model.orEmpty() },
            title = incoming.title.trim().take(250).ifBlank { old?.title.orEmpty() },
            date = incoming.date.trim().take(10).ifBlank { old?.date.orEmpty() },
            target = if (hasTransfer) incoming.target.take(60) else old?.target.orEmpty(),
            outcome = if (hasTransfer) incoming.outcome.take(80) else old?.outcome.orEmpty(),
            completed = if (hasTransfer) incoming.completed.coerceAtLeast(0) else old?.completed ?: 0,
            total = if (hasTransfer) incoming.total.coerceAtLeast(0) else old?.total ?: 0,
            updatedAt = maxOf(incoming.updatedAt, old?.updatedAt ?: 0)
        )
        return (existing.filterNot { GalleryKeys.same(it.url, url) } + entry)
            .sortedWith(compareByDescending<CatalogGallery> { it.updatedAt }.thenBy { it.url })
            .take(LIMIT)
    }

    @Synchronized fun load(context: Context): List<CatalogGallery> =
        decode(context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null))

    @Synchronized private fun write(context: Context, entries: List<CatalogGallery>): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, encode(entries)).commit()

    @Synchronized fun remember(context: Context, url: String, fields: GalleryFields): Boolean {
        val canonical = GalleryKeys.canonical(url) ?: return false
        if (fields.model.isBlank() || fields.title.isBlank() || !GalleryKeys.validDate(fields.date.trim())) return false
        return write(context, merge(load(context),
            CatalogGallery(canonical, fields.model, fields.title, fields.date,
                System.currentTimeMillis())))
    }

    /** Call only for a queue with a real, saved source page URL. */
    @Synchronized fun recordTransfer(context: Context, queue: QueueStore.Snapshot, outcome: String): Boolean {
        val canonical = GalleryKeys.canonical(queue.sourceUrl) ?: return false
        return write(context, merge(load(context), CatalogGallery(
            canonical, queue.model, queue.title, queue.date,
            System.currentTimeMillis(), queue.target, outcome,
            queue.entries.count { it.state == "DONE" || it.state == "SKIPPED" },
            queue.entries.size
        )))
    }

    /** Does not delete local gallery metadata, queue, history or remote files. */
    @Synchronized fun remove(context: Context, url: String): Boolean {
        val canonical = GalleryKeys.canonical(url) ?: return false
        return write(context, load(context).filterNot { GalleryKeys.same(it.url, canonical) })
    }
}
