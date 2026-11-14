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

    @Test fun failedEntryCanBeRetried() {
        val failed = QueueStore.applyItemResult(queue(photo), 0, photo, "FAILED")
        val retried = QueueStore.applyItemResult(failed, 0, photo, "DONE")
        assertEquals("DONE", retried.entries.single().state)
    }
}
