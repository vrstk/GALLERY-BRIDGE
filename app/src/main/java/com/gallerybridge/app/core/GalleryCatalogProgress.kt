package com.gallerybridge.app.core

import com.gallerybridge.app.transfer.QueueStore

/**
 * Read-only local transfer evidence. A DONE/SKIPPED checkpoint is NOT proof that
 * Android SAF or Mail WebDAV has finished syncing the bytes to cloud storage.
 */
internal object GalleryCatalogProgress {
    enum class Kind { SAVED, QUEUED, REVIEW, ERROR, PARTIAL, PROCESSED, UNKNOWN_TRANSFER }
    enum class Filter { ALL, NEW, TRANSFERRED, QUEUED, REVIEW, ERRORS }

    data class Status(
        val kind: Kind, val title: String, val detail: String,
        val linkedQueue: Boolean, val failedCount: Int = 0,
        val hasTransferAttempt: Boolean = false
    ) {
        val needsAttention: Boolean get() =
            kind == Kind.REVIEW || kind == Kind.ERROR || kind == Kind.PARTIAL
        val hasErrors: Boolean get() = failedCount > 0 || kind == Kind.ERROR
    }

    /** Index a mixed batch only once, not once for each of thousands of cards. */
    data class QueueIndex(
        val snapshot: QueueStore.Snapshot?,
        val byGallery: Map<String, List<QueueStore.Entry>>
    )

    fun index(queue: QueueStore.Snapshot?): QueueIndex {
        if (queue == null) return QueueIndex(null, emptyMap())
        val groups = queue.entries.asSequence().mapNotNull { record ->
            GalleryKeys.canonical(record.galleryUrl)
                ?.takeIf { it.isNotBlank() }
                ?.let { it to record }
        }.groupBy({ it.first }, { it.second })
        return QueueIndex(queue, groups)
    }

    fun resolve(entry: CatalogGallery, queue: QueueStore.Snapshot?): Status =
        resolve(entry, index(queue))

    fun resolve(entry: CatalogGallery, index: QueueIndex): Status {
        val queue = index.snapshot
        val key = GalleryKeys.canonical(entry.url)
        val batchEntries = key?.let { index.byGallery[it] }.orEmpty()
        val single = queue != null && queue.sourceUrl.isNotBlank() &&
            GalleryKeys.same(entry.url, queue.sourceUrl)
        val linkedEntries: List<QueueStore.Entry>? = when {
            batchEntries.isNotEmpty() -> batchEntries
            single -> queue!!.entries
            else -> null
        }

        if (linkedEntries != null) {
            val total = linkedEntries.size
            val done = linkedEntries.count { it.state == "DONE" || it.state == "SKIPPED" }
            val blocked = linkedEntries.count { it.state == "BLOCKED" }
            val failed = linkedEntries.count { it.state == "FAILED" }
            val remaining = total - done
            val attempt = entry.target.isNotBlank() || done > 0 || failed > 0 || blocked > 0
            val counts = "Очередь: обработано $done/$total"
            return when {
                blocked > 0 -> Status(Kind.REVIEW, "Требует проверки",
                    "$counts; требуют проверки $blocked" +
                        if (failed > 0) "; ошибок $failed" else "",
                    true, failed, attempt)
                failed > 0 -> Status(Kind.ERROR, "Ошибка передачи",
                    "$counts; ошибок $failed; осталось $remaining", true, failed, attempt)
                remaining > 0 -> Status(Kind.QUEUED, "Есть в очереди",
                    "$counts; осталось $remaining", true, 0, attempt)
                total > 0 -> Status(Kind.PROCESSED, "Очередь обработана",
                    "Обработано $done/$total; синхронизация облака не подтверждена",
                    true, 0, attempt)
                else -> Status(Kind.UNKNOWN_TRANSFER, "Пустая очередь",
                    "Нет фотографий для передачи", true, 0, attempt)
            }
        }

        if (entry.target.isBlank()) {
            return Status(Kind.SAVED, "Сохранена ссылка",
                "Нет локальных сведений о передаче файлов", false)
        }
        val progress = "Последняя передача: ${entry.completed}/${entry.total}"
        val errorOutcome = listOf("ошиб", "сбой", "не удалось").any {
            entry.outcome.contains(it, ignoreCase = true)
        }
        return when {
            errorOutcome -> Status(Kind.ERROR, "Ошибка передачи",
                "$progress; ${entry.outcome}", false, 1, true)
            entry.total <= 0 -> Status(Kind.UNKNOWN_TRANSFER, "История передачи",
                "$progress; ${entry.outcome.ifBlank { "исход не указан" }}", false, 0, true)
            entry.completed < entry.total || entry.completed > entry.total ->
                Status(Kind.PARTIAL, "Обработано частично",
                    "$progress; ${entry.outcome.ifBlank { "остались необработанные файлы" }}",
                    false, 0, true)
            else -> Status(Kind.PROCESSED, "Передача обработана",
                "$progress; облачная синхронизация не подтверждена", false, 0, true)
        }
    }

    fun matches(entry: CatalogGallery, status: Status, filter: Filter): Boolean = when (filter) {
        Filter.ALL -> true
        Filter.NEW -> status.kind == Kind.SAVED
        Filter.TRANSFERRED -> status.hasTransferAttempt
        Filter.QUEUED -> status.linkedQueue
        Filter.REVIEW -> status.needsAttention
        Filter.ERRORS -> status.hasErrors
    }

    fun filter(entries: List<CatalogGallery>, index: QueueIndex, filter: Filter): List<CatalogGallery> =
        entries.filter { entry -> matches(entry, resolve(entry, index), filter) }

    /** Compatibility with older saved/UI filters; no mutation or remote checks. */
    fun filter(entries: List<CatalogGallery>, queue: QueueStore.Snapshot?,
               onlyQueued: Boolean, onlyAttention: Boolean): List<CatalogGallery> {
        val lookup = index(queue)
        return entries.filter { entry ->
            val status = resolve(entry, lookup)
            (!onlyQueued || status.linkedQueue) && (!onlyAttention || status.needsAttention)
        }
    }
}
