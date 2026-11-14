package com.gallerybridge.app.transfer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A compact, local transfer history. No passwords, tokens or media URLs stored here.
 * Repeated attempts on the same queue update one row instead of multiplying entries.
 */
object HistoryStore {
    private const val PREF = "gallery_bridge_history"
    private const val KEY = "records"
    private const val MAX_RECORDS = 40

    data class Record(
        val id: Long,
        val updatedAt: Long,
        val model: String,
        val title: String,
        val date: String,
        val target: String,
        val total: Int,
        val uploaded: Int,
        val skipped: Int,
        val failed: Int,
        val outcome: String
    ) {
        val remaining: Int get() = total - uploaded - skipped
    }

    @Synchronized fun load(context: Context): List<Record> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val x = arr.getJSONObject(i)
                    add(Record(
                        x.getLong("id"), x.getLong("updatedAt"),
                        x.optString("model"), x.optString("title"),
                        x.optString("date"), x.optString("target"),
                        x.optInt("total"), x.optInt("uploaded"),
                        x.optInt("skipped"), x.optInt("failed"),
                        x.optString("outcome")
                    ))
                }
            }.sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized fun record(context: Context, queue: QueueStore.Snapshot, outcome: String): Boolean {
        val now = System.currentTimeMillis()
        val list = listOf(
            Record(
                id = queue.id,
                updatedAt = now,
                model = queue.model,
                title = queue.title,
                date = queue.date,
                target = queue.target,
                total = queue.entries.size,
                uploaded = queue.entries.count { it.state == "DONE" },
                skipped = queue.entries.count { it.state == "SKIPPED" },
                failed = queue.entries.count { it.state == "FAILED" },
                outcome = outcome
            )
        ) + load(context).filterNot { it.id == queue.id }
        val arr = JSONArray()
        list.sortedByDescending { it.updatedAt }.take(MAX_RECORDS).forEach {
            arr.put(JSONObject().put("id", it.id)
                .put("updatedAt", it.updatedAt)
                .put("model", it.model).put("title", it.title)
                .put("date", it.date).put("target", it.target)
                .put("total", it.total).put("uploaded", it.uploaded)
                .put("skipped", it.skipped).put("failed", it.failed)
                .put("outcome", it.outcome))
        }
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY, arr.toString()).commit()
    }
}
