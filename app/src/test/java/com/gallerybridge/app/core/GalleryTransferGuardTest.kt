package com.gallerybridge.app.core

import com.gallerybridge.app.transfer.QueueStore
import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryTransferGuardTest {
    private val url = "https://example.org/model/gallery"
    private val saved = CatalogGallery(url, "Model", "Gallery", "", 100L)
    private val photo = Media("https://cdn.example.org/photo.jpg", "photo.jpg", Kind.PHOTO, "source")
    private fun queue(state: String, page: String = url) = QueueStore.Snapshot(
        1L, "Model", "Gallery", "", "Mail WebDAV", "Models", "",
        listOf(QueueStore.Entry(photo, state)), page
    )

    @Test fun savedMetadataAloneIsNeverProofOfUpload() {
        assertEquals(GalleryTransferGuard.Decision.CLEAR,
            GalleryTransferGuard.assess(url, listOf(saved), null))
    }

    @Test fun previousSuccessRequiresReviewEvenWithNoActiveQueue() {
        assertEquals(GalleryTransferGuard.Decision.REVIEW,
            GalleryTransferGuard.assess(url,
                listOf(saved.copy(target = "Папка Android", outcome = "Завершено", completed = 5, total = 5)), null))
    }

    @Test fun partialAndUnknownPastTransferAlsoRequireReview() {
        for (progress in listOf(0 to 0, 1 to 5)) {
            assertEquals(GalleryTransferGuard.Decision.REVIEW,
                GalleryTransferGuard.assess(url,
                    listOf(saved.copy(target = "Mail WebDAV", completed = progress.first, total = progress.second)),
                    null))
        }
    }

    @Test fun completedQueueCannotBeSilentlyRestartedEvenIfCatalogWriteFailed() {
        assertEquals(GalleryTransferGuard.Decision.REVIEW,
            GalleryTransferGuard.assess(url, listOf(saved), queue("DONE")))
        assertEquals(GalleryTransferGuard.Decision.REVIEW,
            GalleryTransferGuard.assess(url, listOf(saved), queue("SKIPPED")))
    }

    @Test fun uncertainAndPendingQueueMustBeHandledWithoutRequeue() {
        for (state in listOf("PENDING", "FAILED", "BLOCKED")) {
            assertEquals(GalleryTransferGuard.Decision.BLOCKED,
                GalleryTransferGuard.assess(url, listOf(saved), queue(state)))
        }
    }

    @Test fun matchingCanonicalUrlIsStillProtected() {
        assertEquals(GalleryTransferGuard.Decision.BLOCKED,
            GalleryTransferGuard.assess("https://EXAMPLE.ORG/model/gallery/",
                listOf(saved), queue("BLOCKED")))
    }

    @Test fun otherGalleryQueueDoesNotMislabelFreshGallery() {
        assertEquals(GalleryTransferGuard.Decision.CLEAR,
            GalleryTransferGuard.assess(url, listOf(saved),
                queue("BLOCKED", "https://example.org/another")))
    }

    @Test fun recordedPhotoMatchesRequireReviewEvenWithoutCatalogTransfer() {
        assertEquals(GalleryTransferGuard.Decision.REVIEW,
            GalleryTransferGuard.assess(url, emptyList(), null, hasRecordedMatches = true))
        assertEquals(GalleryTransferGuard.Decision.CLEAR,
            GalleryTransferGuard.assess(url, emptyList(), null, hasRecordedMatches = false))
    }

    @Test fun sourceLessLegacyAndManualImportsDoNotGuessPriorTransfers() {
        assertEquals(GalleryTransferGuard.Decision.CLEAR,
            GalleryTransferGuard.assess("", listOf(saved), queue("DONE")))
        assertEquals(GalleryTransferGuard.Decision.CLEAR,
            GalleryTransferGuard.assess("http://example.org/model/gallery", listOf(saved), queue("DONE")))
    }
}
