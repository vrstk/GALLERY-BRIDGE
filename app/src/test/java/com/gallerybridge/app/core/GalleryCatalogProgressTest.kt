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
}
