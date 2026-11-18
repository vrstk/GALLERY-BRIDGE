package com.gallerybridge.app.core

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Small private last-view snapshot. Never stores passwords, transfer keys,
 * queue mutation commands, cookies or photo contents. A restored snapshot
 * is for viewing only; transfers always require a new explicit user action.
 */
internal object GallerySearchSessionStore {
    private const val FILE = "gallery-search-session.json"
    private const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_ITEMS = 300

    data class Snapshot(
        val url: String,
        val model: String,
        val title: String,
        val date: String,
        val screen: String,
        val items: List<Media>,
        val selected: Set<String>,
        val recommended: Set<String>,
        /** Current viewer position, if still part of the persisted photo list. */
        val openedPhoto: String? = null
    )

    private fun safeMediaAddress(link: String): Boolean {
        val parsed = runCatching { URI(link) }.getOrNull() ?: return false
        return parsed.scheme.equals("https", true) ||
            parsed.scheme.equals("content", true)
    }

    internal fun encode(snapshot: Snapshot): String {
        val items = JSONArray()
        val storedPhotos = snapshot.items.asSequence()
            .filter { it.kind == Kind.PHOTO && safeMediaAddress(it.url) }
            .take(MAX_ITEMS).toList()
        storedPhotos.forEach { item ->
                items.put(JSONObject().put("url", item.url).put("name", item.name)
                    .put("source", item.source).put("referrer", item.referrer))
            }
        return JSONObject().put("format", 1)
            .put("url", snapshot.url.take(2048))
            .put("model", snapshot.model.take(130))
            .put("title", snapshot.title.take(250))
            .put("date", snapshot.date.take(10))
            .put("screen", snapshot.screen.take(30))
            .put("items", items)
            .put("selected", JSONArray(snapshot.selected.take(MAX_ITEMS)))
            .put("recommended", JSONArray(snapshot.recommended.take(MAX_ITEMS)))
            .put("openedPhoto", snapshot.openedPhoto?.takeIf {
                snapshot.screen == "DOWNLOADS" && storedPhotos.any { photo -> photo.url == it }
            } ?: JSONObject.NULL)
            .toString()
    }

    internal fun decode(raw: String): Snapshot? = runCatching {
        val json = JSONObject(raw)
        require(json.getInt("format") == 1)
        val link = json.optString("url")
        require(link.isEmpty() || GalleryKeys.canonical(link) != null)
        val items = mutableListOf<Media>()
        val array = json.getJSONArray("items")
        require(array.length() <= MAX_ITEMS)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val address = item.getString("url")
            if (!safeMediaAddress(address)) continue
            items.add(Media(address, item.optString("name").take(130),
                Kind.PHOTO, item.optString("source").take(150),
                item.optString("referrer").take(2048)))
        }
        val urls = items.mapTo(HashSet()) { it.url }
        fun selected(name: String): Set<String> {
            val output = linkedSetOf<String>()
            val array = json.optJSONArray(name) ?: return output
            for (i in 0 until minOf(array.length(), MAX_ITEMS)) {
                val url = array.optString(i)
                if (url in urls) output.add(url)
            }
            return output
        }
        Snapshot(link, json.optString("model").take(130),
            json.optString("title").take(250), json.optString("date").take(10),
            json.optString("screen").take(30), items.distinctBy(Media::url),
            selected("selected"), selected("recommended"),
            json.optString("openedPhoto").takeIf {
                it in urls && json.optString("screen") == "DOWNLOADS"
            })
    }.getOrNull()

    @Synchronized fun load(context: Context): Snapshot? = runCatching {
        AtomicFile(context.getFileStreamPath(FILE)).openRead().use {
            val bytes = it.readBytes()
            require(bytes.size in 1..MAX_BYTES)
            decode(String(bytes, Charsets.UTF_8))
        }
    }.getOrNull()

    @Synchronized fun save(context: Context, snapshot: Snapshot) {
        val data = encode(snapshot).toByteArray(Charsets.UTF_8)
        require(data.size <= MAX_BYTES) { "Слишком много данных просмотра." }
        val file = AtomicFile(context.getFileStreamPath(FILE))
        val out = file.startWrite()
        try {
            out.write(data)
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }
}
