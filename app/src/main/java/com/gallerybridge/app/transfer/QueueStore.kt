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
        val account: String,
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
                data.getString("root"), data.optString("account", ""), entries)
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

    /**
     * Only the exact queue entry that was processed may change.
     * URLs can repeat across entries with different names, and even identical
     * media can appear more than once in an imported queue.
     */
    fun applyItemResult(snapshot: Snapshot, entryIndex: Int, media: Media, state: String): Snapshot {
        require(state in setOf("DONE", "SKIPPED", "FAILED")) { "Invalid queue state: $state" }
        val old = snapshot.entries.getOrNull(entryIndex) ?: return snapshot
        if (old.media != media || old.state == "DONE" || old.state == "SKIPPED") return snapshot
        val entries = snapshot.entries.toMutableList()
        entries[entryIndex] = old.copy(state = state)
        return snapshot.copy(entries = entries)
    }

    @Synchronized fun checkpoint(context: Context, id: Long, entryIndex: Int, media: Media, state: String) {
        val before = load(context) ?: return
        if (before.id != id) return
        val after = applyItemResult(before, entryIndex, media, state)
        if (after != before) save(context, after)
    }

    fun newSnapshot(
        model: String, title: String, date: String, target: String,
        root: String, account: String, files: List<Media>
    ): Snapshot = Snapshot(System.currentTimeMillis(), model, title, date, target,
        root, account, files.map { Entry(it, "PENDING") })
}
