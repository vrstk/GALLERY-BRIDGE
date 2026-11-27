package com.gallerybridge.app.transfer

import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class QueueStoreTest {
    private val hash = "a".repeat(64)
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

    @Test fun writeIntentCannotBeRetriedAndOnlyAConfirmedResultCompletesIt() {
        val original = queue(photo, photo.copy(name = "other.jpg"))
        val writing = QueueStore.markWriting(original, 0, photo, hash)
        assertEquals("WRITING", writing.entries[0].state)
        assertEquals("BLOCKED", QueueStore.restoreState("WRITING", true))
        assertEquals(listOf(1), QueueStore.retryIndices(writing))
        assertSame(writing, QueueStore.markWriting(writing, 0, photo, hash))
        val blocked = QueueStore.applyItemResult(writing, 0, photo, "FAILED")
        assertEquals("BLOCKED", blocked.entries[0].state)
        assertEquals(listOf(1), QueueStore.retryIndices(blocked))
        val done = QueueStore.applyItemResult(writing, 0, photo, "DONE",
            contentSha256 = "a".repeat(64))
        assertEquals("DONE", done.entries[0].state)
        assertEquals("a".repeat(64), done.entries[0].contentSha256)
        assertEquals("PENDING", done.entries[1].state)
        assertSame(original, QueueStore.markWriting(original, 0, photo.copy(name = "wrong.jpg"), hash))
    }

    @Test fun blockedWriteRetainsItsDigestAndOnlyMatchingReadbackCompletesIt() {
        val original = queue(photo, photo.copy(name = "other.jpg"))
        val writing = QueueStore.markWriting(original, 0, photo, hash)
        assertEquals(hash, writing.entries[0].contentSha256)
        val blocked = QueueStore.applyItemResult(writing, 0, photo, "BLOCKED")
        assertEquals(hash, blocked.entries[0].contentSha256)
        assertSame(blocked, QueueStore.confirmBlocked(blocked, 0, "b".repeat(64)))
        val confirmed = QueueStore.confirmBlocked(blocked, 0, hash)
        assertEquals(listOf("DONE", "PENDING"), confirmed.entries.map { it.state })
        assertEquals(hash, confirmed.entries[0].contentSha256)
        assertEquals("", QueueStore.allowRetry(blocked, 0, photo).entries[0].contentSha256)
    }

    @Test fun successfulPhotoCheckpointRetainsVerifiedSourceDigest() {
        val hash = "a".repeat(64)
        val done = QueueStore.applyItemResult(queue(photo), 0, photo, "DONE", contentSha256 = hash)
        assertEquals("DONE", done.entries.single().state)
        assertEquals(hash, done.entries.single().contentSha256)
        assertEquals(0, done.remaining)
    }

    @Test fun oldCheckpointWithoutDigestRemainsReadable() {
        val done = QueueStore.applyItemResult(queue(photo), 0, photo, "DONE")
        assertEquals("", done.entries.single().contentSha256)
    }

    @Test fun failuresCannotRetainOrInventSourceHash() {
        val hash = "b".repeat(64)
        val failed = QueueStore.applyItemResult(queue(photo), 0, photo, "FAILED", contentSha256 = hash)
        assertEquals("", failed.entries.single().contentSha256)
        val restored = QueueStore.applyItemResult(failed, 0, photo, "SKIPPED", contentSha256 = hash)
        assertEquals(hash, restored.entries.single().contentSha256)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            QueueStore.applyItemResult(queue(photo), 0, photo, "DONE", contentSha256 = "bad-hash")
        }
    }

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
    @Test fun onePhotoRetryDoesNotTouchPendingDoneOrBlockedEntries() {
        val second = photo.copy(name = "b.jpg")
        val third = photo.copy(name = "c.jpg")
        val fourth = photo.copy(name = "d.jpg")
        val initial = queue(photo, second, third, fourth)
        val failed = QueueStore.applyItemResult(initial, 1, second, "FAILED", "Нет сети")
        val blocked = QueueStore.applyItemResult(failed, 2, third, "BLOCKED", "Неясный результат")
        val done = QueueStore.applyItemResult(blocked, 3, fourth, "DONE")
        assertEquals(listOf(0, 1), QueueStore.retryIndices(done))
        assertEquals(listOf(1), QueueStore.retryIndices(done, 1))
        assertEquals(emptyList<Int>(), QueueStore.retryIndices(done, 0))
        assertEquals(emptyList<Int>(), QueueStore.retryIndices(done, 2))
        assertEquals(emptyList<Int>(), QueueStore.retryIndices(done, 3))
        assertEquals(emptyList<Int>(), QueueStore.retryIndices(done, 200))
        assertEquals(listOf("PENDING", "FAILED", "BLOCKED", "DONE"), done.entries.map { it.state })
    }

    @Test fun selectedRetryRejectsLegacyVideoFailure() {
        val video = Media("https://example.com/old.mp4", "old.mp4", Kind.VIDEO, "test")
        val oldQueue = queue(video)
        val failed = QueueStore.applyItemResult(oldQueue, 0, video, "FAILED", "Старая версия")
        assertEquals(emptyList<Int>(), QueueStore.retryIndices(failed, 0))
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

    @Test fun missingOrUnrecognizedStateRequiresManualReview() {
        for (state in listOf("", "null", "UNKNOWN", "pending", "DONE ")) {
            assertEquals("BLOCKED", QueueStore.restoreState(state, false))
            assertEquals("BLOCKED", QueueStore.restoreState(state, true))
        }
        for (state in listOf("PENDING", "FAILED", "DONE", "SKIPPED", "BLOCKED")) {
            assertEquals(state, QueueStore.restoreState(state, true))
        }
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
