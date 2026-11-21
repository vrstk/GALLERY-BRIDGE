package com.gallerybridge.app.core

import com.gallerybridge.app.transfer.QueueStore

/** Read-only status; neither durable queue nor remote files are modified. */
internal object GalleryCatalogProgress {
    enum class Kind { SAVED, QUEUED, REVIEW, PARTIAL, PROCESSED, UNKNOWN_TRANSFER }

    data class Status(val kind: Kind, val title: String, val detail: String,
                      val linkedQueue: Boolean) {
        val needsAttention: Boolean get() = kind == Kind.REVIEW || kind == Kind.PARTIAL
    }

    fun resolve(entry: CatalogGallery, queue: QueueStore.Snapshot?): Status {
        val linked = queue != null && GalleryKeys.same(entry.url, queue.sourceUrl)
        if (linked && queue != null) {
            val total = queue.entries.size
            val completed = queue.completed
            val blocked = queue.blocked
            return when {
                blocked > 0 -> Status(Kind.REVIEW, "Требует проверки",
                    "Очередь: обработано $completed/$total; требуют проверки $blocked", true)
                queue.remaining > 0 -> Status(Kind.QUEUED, "Есть в очереди",
                    "Очередь: обработано $completed/$total; осталось ${queue.remaining}", true)
                total > 0 -> Status(Kind.PROCESSED, "Очередь обработана",
                    "Обработано $completed/$total; синхронизация облака не подтверждена", true)
                else -> Status(Kind.UNKNOWN_TRANSFER, "Пустая очередь",
                    "Нет фотографий для передачи", true)
            }
        }
        if (entry.target.isBlank()) {
            return Status(Kind.SAVED, "Сохранена ссылка", "Передач по этой записи ещё нет", false)
        }
        val progress = "Последняя передача: ${entry.completed}/${entry.total}"
        return when {
            entry.total <= 0 -> Status(Kind.UNKNOWN_TRANSFER, "История передачи",
                "$progress; ${entry.outcome.ifBlank { "исход не указан" }}", false)
            entry.completed < entry.total -> Status(Kind.PARTIAL, "Обработано частично",
                "$progress; ${entry.outcome.ifBlank { "остались необработанные файлы" }}", false)
            else -> Status(Kind.PROCESSED, "Передача обработана",
                "$progress; облачная синхронизация не подтверждена", false)
        }
    }

    fun filter(entries: List<CatalogGallery>, queue: QueueStore.Snapshot?,
               onlyQueued: Boolean, onlyAttention: Boolean): List<CatalogGallery> =
        entries.filter { entry ->
            val status = resolve(entry, queue)
            (!onlyQueued || status.linkedQueue) && (!onlyAttention || status.needsAttention)
        }
}
