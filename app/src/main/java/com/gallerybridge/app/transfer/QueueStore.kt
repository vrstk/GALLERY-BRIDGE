package com.gallerybridge.app.transfer

import android.content.Context
import com.gallerybridge.app.core.Kind
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
        val entries: List<Entry>
    ) {
        val remaining: Int get() = entries.count { it.state != "DONE" && it.state != "SKIPPED" }
        val completed: Int get() = entries.count { it.state == "DONE" || it.state == "SKIPPED" }
    }
    data class Entry(val media: Media, val state: String)

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
                        Kind.valueOf(x.getString("kind")), x.optString("source")),
                        x.optString("state", "PENDING")))
                }
            }
            Snapshot(data.getLong("id"), data.getString("model"), data.getString("title"),
                data.optString("date"), data.getString("target"),
                data.getString("root"), entries)
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
        val entries = JSONArray()
        for (entry in snapshot.entries) {
            entries.put(JSONObject()
                .put("url", entry.media.url).put("name", entry.media.name)
                .put("kind", entry.media.kind.name)
                .put("source", entry.media.source).put("state", entry.state))
        }
        json.put("entries", entries)
        // Synchronous commit for durable checkpoints.
        storage(context).edit().putString(KEY, json.toString()).commit()
    }

    @Synchronized fun checkpoint(context: Context, id: Long, url: String, state: String) {
        val before = load(context) ?: return
        if (before.id != id) return
        save(context, before.copy(entries = before.entries.map {
            if (it.media.url == url) it.copy(state = state) else it
        }))
    }

    fun newSnapshot(
        model: String, title: String, date: String, target: String,
        root: String, files: List<Media>
    ): Snapshot = Snapshot(System.currentTimeMillis(), model, title, date, target,
        root, files.map { Entry(it, "PENDING") })
}
