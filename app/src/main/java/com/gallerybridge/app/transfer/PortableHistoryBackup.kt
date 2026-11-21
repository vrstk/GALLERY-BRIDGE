package com.gallerybridge.app.transfer

import org.json.JSONArray
import org.json.JSONObject

/** Bounded, credential-free history serialization used only for portable backups. */
object PortableHistoryBackup {
    const val LIMIT = 40

    data class Plan(
        val records: List<HistoryStore.Record>,
        val added: Int,
        val alreadyPresent: Int,
        val noSpace: Int
    )

    fun encode(records: List<HistoryStore.Record>): JSONArray {
        require(records.size <= LIMIT) { "История содержит слишком много записей" }
        val output = JSONArray()
        records.forEach { record ->
            output.put(
                JSONObject().put("id", record.id).put("updatedAt", record.updatedAt)
                    .put("model", record.model).put("title", record.title)
                    .put("date", record.date).put("target", record.target)
                    .put("total", record.total).put("uploaded", record.uploaded)
                    .put("skipped", record.skipped).put("failed", record.failed)
                    .put("outcome", record.outcome)
            )
        }
        return output
    }

    /** Reject the entire backup on malformed, duplicate or inconsistent history rows. */
    fun decode(raw: JSONArray): List<HistoryStore.Record> {
        require(raw.length() <= LIMIT) { "Слишком много записей истории" }
        val ids = HashSet<Long>()
        val rows = ArrayList<HistoryStore.Record>(raw.length())
        for (i in 0 until raw.length()) {
            val row = raw.getJSONObject(i)
            val id = row.getLong("id")
            val timestamp = row.getLong("updatedAt")
            val model = row.getString("model")
            val title = row.getString("title")
            val date = row.getString("date")
            val target = row.getString("target")
            val outcome = row.getString("outcome")
            val total = row.getInt("total")
            val uploaded = row.getInt("uploaded")
            val skipped = row.getInt("skipped")
            val failed = row.getInt("failed")
            require(id > 0 && timestamp >= 0 && ids.add(id)) { "Повторяющаяся или неверная запись истории" }
            require(total >= 0 && uploaded >= 0 && skipped >= 0 && failed >= 0 &&
                uploaded.toLong() + skipped <= total && failed <= total) {
                "Некорректные счётчики передачи"
            }
            require(model.length <= 250 && title.length <= 500 && date.length <= 20 &&
                target.length <= 100 && outcome.length <= 150) { "Слишком длинное поле истории" }
            rows.add(HistoryStore.Record(id, timestamp, model, title, date, target,
                total, uploaded, skipped, failed, outcome))
        }
        return rows.sortedWith(compareByDescending<HistoryStore.Record> { it.updatedAt }.thenBy { it.id })
    }

    /** Local records always win on a matching ID; no prior outcome is overwritten. */
    fun plan(current: List<HistoryStore.Record>, incoming: List<HistoryStore.Record>): Plan {
        val existingIds = current.mapTo(HashSet<Long>()) { it.id }
        val uniqueNew = incoming.filter { it.id !in existingIds }
            .sortedWith(compareByDescending<HistoryStore.Record> { it.updatedAt }.thenBy { it.id })
        val space = (LIMIT - current.size).coerceAtLeast(0)
        val restored = uniqueNew.take(space)
        return Plan(
            records = (current + restored)
                .sortedWith(compareByDescending<HistoryStore.Record> { it.updatedAt }.thenBy { it.id })
                .take(LIMIT),
            added = restored.size,
            alreadyPresent = incoming.size - uniqueNew.size,
            noSpace = uniqueNew.size - restored.size
        )
    }
}
