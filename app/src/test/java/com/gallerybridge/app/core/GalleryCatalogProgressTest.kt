package com.gallerybridge.app.core

import com.gallerybridge.app.transfer.QueueStore
import org.junit.Assert.*
import org.junit.Test

class GalleryCatalogProgressTest {
    private val url = "https://example.com/gallery/one"
    private val entry = CatalogGallery(url, "Модель", "Публикация", "", 123L)
    private val media = Media("https://cdn.example.com/1.jpg", "1.jpg", Kind.PHOTO, "Тест")
    private fun queue(vararg states: String, source: String = url) =
        QueueStore.Snapshot(42, "Модель", "Публикация", "", "Mail WebDAV", "Models", "",
            states.map { QueueStore.Entry(media, it) }, source)

    @Test fun savedMetadataNeverPretendsPhotosWereUploaded() {
        val result = GalleryCatalogProgress.resolve(entry, null)
        assertEquals(GalleryCatalogProgress.Kind.SAVED, result.kind)
        assertFalse(result.linkedQueue)
    }

    @Test fun completedHistoryDoesNotClaimCloudSynchronization() {
        val prior = entry.copy(target = "Папка Android", completed = 3, total = 3, outcome = "Завершено")
        val result = GalleryCatalogProgress.resolve(prior, null)
        assertEquals(GalleryCatalogProgress.Kind.PROCESSED, result.kind)
        assertTrue(result.detail.contains("не подтверждена"))
    }

    @Test fun incompleteHistoricalTransferNeedsAttention() {
        val prior = entry.copy(target = "Mail WebDAV", completed = 1, total = 4, outcome = "Остановлено")
        assertEquals(GalleryCatalogProgress.Kind.PARTIAL,
            GalleryCatalogProgress.resolve(prior, null).kind)
        assertTrue(GalleryCatalogProgress.resolve(prior, null).needsAttention)
    }

    @Test fun activeQueueTakesPrecedenceOverOldSuccessfulHistory() {
        val prior = entry.copy(target = "Mail WebDAV", completed = 12, total = 12)
        val result = GalleryCatalogProgress.resolve(prior, queue("DONE", "PENDING"))
        assertEquals(GalleryCatalogProgress.Kind.QUEUED, result.kind)
        assertEquals("Очередь: обработано 1/2; осталось 1", result.detail)
        assertTrue(result.linkedQueue)
    }

    @Test fun blockedItemCannotBeDisplayedAsAutomaticallyRetryable() {
        val result = GalleryCatalogProgress.resolve(entry, queue("DONE", "BLOCKED"))
        assertEquals(GalleryCatalogProgress.Kind.REVIEW, result.kind)
        assertTrue(result.needsAttention)
        assertTrue(result.detail.contains("требуют проверки 1"))
    }

    @Test fun otherQueueNeverOverwritesSavedStatus() {
        val result = GalleryCatalogProgress.resolve(entry,
            queue("BLOCKED", source = "https://example.com/gallery/other"))
        assertEquals(GalleryCatalogProgress.Kind.SAVED, result.kind)
        assertFalse(result.linkedQueue)
    }

    @Test fun equivalentCanonicalGalleryUrlsLinkToQueue() {
        val result = GalleryCatalogProgress.resolve(entry,
            queue("PENDING", source = "https://EXAMPLE.COM/gallery/one/"))
        assertEquals(GalleryCatalogProgress.Kind.QUEUED, result.kind)
    }

    @Test fun filtersDoNotModifyDataAndNeverCreateQueueEntries() {
        val attention = entry.copy(url = "https://example.com/gallery/old", target = "Mail WebDAV",
            total = 5, completed = 2)
        val records = listOf(entry, attention)
        val inQueue = GalleryCatalogProgress.filter(records, queue("PENDING"), true, false)
        val warnings = GalleryCatalogProgress.filter(records, queue("PENDING"), false, true)
        assertEquals(listOf(entry), inQueue)
        assertEquals(listOf(attention), warnings)
        assertEquals(2, records.size)
    }

    @Test fun emptyHistoricalTransferIsUnknownRatherThanCompleted() {
        val prior = entry.copy(target = "Mail WebDAV", completed = 0, total = 0)
        assertEquals(GalleryCatalogProgress.Kind.UNKNOWN_TRANSFER,
            GalleryCatalogProgress.resolve(prior, null).kind)
    }
    @Test fun mixedBatchStatusesAreScopedToEachGalleryNotWholeQueue() {
        val first = entry.copy(url = "https://example.com/gallery/one", title = "One")
        val second = entry.copy(url = "https://example.com/gallery/two", title = "Two")
        val third = entry.copy(url = "https://example.com/gallery/three", title = "Three")
        val fourth = entry.copy(url = "https://example.com/gallery/four", title = "Four")
        val records = listOf(
            QueueStore.Entry(media, "DONE", galleryUrl = first.url),
            QueueStore.Entry(media.copy(name = "2.jpg"), "PENDING", galleryUrl = first.url),
            QueueStore.Entry(media, "FAILED", "HTTP 503", galleryUrl = second.url),
            QueueStore.Entry(media, "BLOCKED", "Неясный результат", galleryUrl = third.url)
        )
        val batch = QueueStore.Snapshot(
            10L, "Модель", "Пакет", "", "Mail WebDAV", "Models", "",
            records, sourceUrl = ""
        )
        val index = GalleryCatalogProgress.index(batch)
        assertEquals(GalleryCatalogProgress.Kind.QUEUED,
            GalleryCatalogProgress.resolve(first, index).kind)
        assertEquals("Очередь: обработано 1/2; осталось 1",
            GalleryCatalogProgress.resolve(first, index).detail)
        assertEquals(GalleryCatalogProgress.Kind.ERROR,
            GalleryCatalogProgress.resolve(second, index).kind)
        assertEquals(GalleryCatalogProgress.Kind.REVIEW,
            GalleryCatalogProgress.resolve(third, index).kind)
        assertEquals(GalleryCatalogProgress.Kind.SAVED,
            GalleryCatalogProgress.resolve(fourth, index).kind)
        assertFalse(GalleryCatalogProgress.resolve(fourth, index).linkedQueue)
        assertEquals(listOf(second), GalleryCatalogProgress.filter(
            listOf(first, second, third, fourth), index,
            GalleryCatalogProgress.Filter.ERRORS))
        assertEquals(listOf(second, third), GalleryCatalogProgress.filter(
            listOf(first, second, third, fourth), index,
            GalleryCatalogProgress.Filter.REVIEW))
    }

    @Test fun blockedAndFailedTogetherAreNotHiddenFromErrorFilter() {
        val records = listOf(
            QueueStore.Entry(media, "BLOCKED", "Неизвестный итог"),
            QueueStore.Entry(media.copy(name = "2.jpg"), "FAILED", "Сеть")
        )
        val queue = queue(source = url).copy(entries = records)
        val status = GalleryCatalogProgress.resolve(entry, queue)
        assertEquals(GalleryCatalogProgress.Kind.REVIEW, status.kind)
        assertEquals(1, status.failedCount)
        assertTrue(status.hasErrors)
        assertTrue(status.needsAttention)
        assertEquals(listOf(entry), GalleryCatalogProgress.filter(
            listOf(entry), GalleryCatalogProgress.index(queue),
            GalleryCatalogProgress.Filter.ERRORS))
    }

    @Test fun pendingOnlyIsNotReportedAsAlreadyTransferred() {
        val queue = queue("PENDING")
        val status = GalleryCatalogProgress.resolve(entry, queue)
        assertEquals(GalleryCatalogProgress.Kind.QUEUED, status.kind)
        assertFalse(status.hasTransferAttempt)
        val indexed = GalleryCatalogProgress.index(queue)
        assertTrue(GalleryCatalogProgress.filter(listOf(entry), indexed,
            GalleryCatalogProgress.Filter.TRANSFERRED).isEmpty())
        assertEquals(listOf(entry), GalleryCatalogProgress.filter(listOf(entry), indexed,
            GalleryCatalogProgress.Filter.QUEUED))
        assertTrue(GalleryCatalogProgress.filter(listOf(entry), indexed,
            GalleryCatalogProgress.Filter.NEW).isEmpty())
    }

    @Test fun historicalErrorAndUnrecordedGalleryHaveSeparateFilters() {
        val history = entry.copy(url = "https://example.com/gallery/error",
            target = "Mail WebDAV", total = 4, completed = 2,
            outcome = "Ошибка подключения")
        val all = listOf(entry, history)
        val idx = GalleryCatalogProgress.index(null)
        assertEquals(GalleryCatalogProgress.Kind.ERROR,
            GalleryCatalogProgress.resolve(history, idx).kind)
        assertEquals(listOf(entry), GalleryCatalogProgress.filter(all, idx,
            GalleryCatalogProgress.Filter.NEW))
        assertEquals(listOf(history), GalleryCatalogProgress.filter(all, idx,
            GalleryCatalogProgress.Filter.TRANSFERRED))
        assertEquals(listOf(history), GalleryCatalogProgress.filter(all, idx,
            GalleryCatalogProgress.Filter.ERRORS))
        assertEquals(all, GalleryCatalogProgress.filter(all, idx,
            GalleryCatalogProgress.Filter.ALL))
        assertTrue(GalleryCatalogProgress.resolve(history, idx).detail
            .contains("Ошибка подключения"))
        // Filtering never changes source records or the saved queue.
        assertEquals("", entry.target)
    }

    @Test fun batchIndexHandlesManyEntriesWithPerGalleryCounts() {
        val records = (1..1500).map { i ->
            QueueStore.Entry(media.copy(name = "photo-${i}.jpg"), "PENDING",
                galleryUrl = "https://example.com/gallery/${i}")
        }
        val batch = queue(source = "").copy(entries = records)
        val index = GalleryCatalogProgress.index(batch)
        assertEquals(1500, index.byGallery.size)
        val one = entry.copy(url = "https://example.com/gallery/1499")
        val result = GalleryCatalogProgress.resolve(one, index)
        assertEquals(GalleryCatalogProgress.Kind.QUEUED, result.kind)
        assertEquals("Очередь: обработано 0/1; осталось 1", result.detail)
    }

}
