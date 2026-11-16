package com.gallerybridge.app.transfer

import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class QueueStoreTest {
    private val photo = Media("https://example.com/photo.jpg", "first.jpg", Kind.PHOTO, "test")

    private fun queue(vararg media: Media): QueueStore.Snapshot = QueueStore.Snapshot(
        id = 42L,
        model = "Example",
        title = "Gallery",
        date = "",
        target = "Mail WebDAV",
        root = "Models",
        account = "",
        entries = media.map { QueueStore.Entry(it, "PENDING") }
    )

    @Test fun sameSourceUrlWithDifferentNamesDoesNotCompleteOtherFiles() {
        val renamed = photo.copy(name = "second.jpg")
        val initial = queue(photo, renamed)
        val updated = QueueStore.applyItemResult(initial, 0, photo, "DONE")

        assertEquals("DONE", updated.entries[0].state)
        assertEquals("PENDING", updated.entries[1].state)
        assertEquals(1, updated.remaining)
    }

    @Test fun identicalMediaEntriesKeepIndependentCheckpoints() {
        val initial = queue(photo, photo)
        val first = QueueStore.applyItemResult(initial, 0, photo, "SKIPPED")
        val second = QueueStore.applyItemResult(first, 1, photo, "DONE")

        assertEquals(listOf("SKIPPED", "DONE"), second.entries.map { it.state })
        assertEquals(0, second.remaining)
    }

    @Test fun mismatchedIndexOrMediaCannotCorruptQueue() {
        val initial = queue(photo)
        assertSame(initial, QueueStore.applyItemResult(initial, 8, photo, "DONE"))
        assertSame(initial, QueueStore.applyItemResult(initial, 0, photo.copy(name = "wrong.jpg"), "DONE"))
    }

    @Test fun completedEntryCannotBeChangedByLateFailure() {
        val initial = QueueStore.applyItemResult(queue(photo), 0, photo, "DONE")
        assertSame(initial, QueueStore.applyItemResult(initial, 0, photo, "FAILED"))
    }

    @Test fun laterFailureCannotChangeCompletedQueueItem() {
        val original = queue(photo)
        val done = QueueStore.applyItemResult(original, 0, photo, "DONE")
        assertSame(done, QueueStore.applyItemResult(done, 0, photo, "FAILED"))
        assertEquals(0, done.remaining)
    }

    @Test fun failedEntryCanBeRetried() {
        val failed = QueueStore.applyItemResult(queue(photo), 0, photo, "FAILED")
        val retried = QueueStore.applyItemResult(failed, 0, photo, "DONE")
        assertEquals("DONE", retried.entries.single().state)
    }
    @Test fun blockedWriteNeverRetriesAutomatically() {
        val initial = queue(photo)
        val blocked = QueueStore.applyItemResult(initial, 0, photo, "BLOCKED", "Проверьте файл")
        assertEquals(1, blocked.blocked)
        assertEquals(0, blocked.retryable)
        assertEquals("Проверьте файл", blocked.entries.single().failureReason)
        assertSame(blocked, QueueStore.applyItemResult(blocked, 0, photo, "DONE"))
    }

    @Test fun manualReviewReleasesOnlyMatchingFile() {
        val other = photo.copy(name = "different.jpg")
        val initial = queue(photo, other)
        val blocked = QueueStore.applyItemResult(initial, 0, photo, "BLOCKED", "Нужна проверка")
        assertSame(blocked, QueueStore.allowRetry(blocked, 0, other))
        val unblocked = QueueStore.allowRetry(blocked, 0, photo)
        assertEquals(listOf("PENDING", "PENDING"), unblocked.entries.map { it.state })
        assertEquals(2, unblocked.retryable)
        assertEquals("", unblocked.entries[0].failureReason)
        assertEquals("PENDING", unblocked.entries[1].state)
    }

    @Test fun legacyFailedIsNotAutomaticallyRetried() {
        assertEquals("BLOCKED", QueueStore.restoreState("FAILED", false))
        assertEquals("FAILED", QueueStore.restoreState("FAILED", true))
        assertEquals("PENDING", QueueStore.restoreState("PENDING", false))
    }

    @Test fun safeFailureCanBeRetriedAndClearsReason() {
        val failed = QueueStore.applyItemResult(queue(photo), 0, photo,
            "FAILED", "Временная ошибка сети")
        assertEquals(1, failed.retryable)
        assertEquals("Временная ошибка сети", failed.entries.single().failureReason)
        val done = QueueStore.applyItemResult(failed, 0, photo, "DONE")
        assertEquals(0, done.remaining)
        assertEquals("", done.entries.single().failureReason)
    }

    @Test fun duplicateFileNamesNeverUnlockWrongQueueEntry() {
        val original = queue(photo, photo)
        val blocked = QueueStore.applyItemResult(original, 1, photo, "BLOCKED", "Проверьте")
        val unblocked = QueueStore.allowRetry(blocked, 1, photo)
        assertEquals("PENDING", unblocked.entries[0].state)
        assertEquals("PENDING", unblocked.entries[1].state)
        assertEquals(2, unblocked.retryable)
    }

}
