package com.gallerybridge.app.core

import com.gallerybridge.app.transfer.QueueStore
import org.junit.Assert.*
import org.junit.Test

class GalleryBatchSelectionTest {
    private val a = CatalogGallery("https://example.org/gallery/a", "A", "One", "", 1)
    private val b = CatalogGallery("https://example.org/gallery/b", "B", "Two", "", 2)
    private val done = CatalogGallery("https://example.org/gallery/done", "C", "Done", "", 3,
        "Mail WebDAV", "Завершено", 12, 12)
    private val item = Media("https://example.org/test.jpg", "test.jpg", Kind.PHOTO, "test")
    private val active = QueueStore.Snapshot(
        1, "A", "One", "", "Mail WebDAV", "Models", "",
        listOf(QueueStore.Entry(item, "BLOCKED")), a.url
    )

    @Test fun canonicalDuplicatesBecomeOneReviewItem() {
        val picked = listOf("https://EXAMPLE.ORG/gallery/a/", a.url, b.url, a.url)
        assertEquals(listOf(a.url, b.url), GalleryBatchSelection.plan(listOf(a, b), picked, null))
    }

    @Test fun previousTransfersAndActiveQueueAreExcluded() {
        val all = listOf(a, b, done)
        val picked = listOf(a.url, done.url, b.url)
        assertEquals(listOf(b.url), GalleryBatchSelection.plan(all, picked, active))
        assertFalse(GalleryBatchSelection.eligible(done, null))
        assertFalse(GalleryBatchSelection.eligible(a, active))
    }

    @Test fun selectionNeverImportsUnknownOrInsecureUrls() {
        val picked = listOf("http://example.org/gallery/a", "https://example.org/other", b.url)
        assertEquals(listOf(b.url), GalleryBatchSelection.plan(listOf(a, b), picked, null))
    }

    @Test fun boundedAndStableOrderingWithoutMutatingCatalog() {
        val entries = (0..40).map { n ->
            CatalogGallery("https://example.org/$n", "Model$n", "Gallery$n", "", n.toLong())
        }
        val selected = entries.map { it.url }
        val plan = GalleryBatchSelection.plan(entries, selected, null)
        assertEquals(GalleryBatchSelection.LIMIT, plan.size)
        assertEquals(selected.take(GalleryBatchSelection.LIMIT), plan)
        assertEquals(41, entries.size)
    }

    @Test fun evenCompletedSavedQueueRequiresManualGalleryReview() {
        val completed = active.copy(entries = listOf(QueueStore.Entry(item, "DONE")))
        assertFalse(GalleryBatchSelection.eligible(a, completed))
        assertEquals(listOf(b.url),
            GalleryBatchSelection.plan(listOf(a, b), listOf(a.url, b.url), completed))
    }

    @Test fun selectionProducesOnlyPageUrlsAndNeverMediaOrCredentials() {
        val selected = GalleryBatchSelection.plan(listOf(a),
            listOf("https://cdn.example.org/test.jpg", a.url), null)
        assertEquals(listOf(a.url), selected)
        assertTrue(selected.all { it.startsWith("https://") })
    }
}
