package com.gallerybridge.app.transfer

import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import org.junit.Assert.*
import org.junit.Test

class PhotoOutcomeLedgerTest {
    private val page = "https://example.org/gallery/one"
    private val media = Media("https://cdn.example.org/1.jpg", "Photo 01.jpg", Kind.PHOTO, "source")
    private val changedUrl = media.copy(url = "https://cdn.example.org/new-image.jpg")
    private fun queue(
        state: String = "DONE", target: String = "Mail WebDAV", root: String = "Models",
        account: String = "test@example.org", source: String = page
    ) = QueueStore.Snapshot(1L, "Model", "Gallery", "", target, root, account,
        listOf(QueueStore.Entry(media, state)), source)

    @Test fun onlyConfirmedQueueOutcomesAreRecorded() {
        assertEquals(0, PhotoOutcomeLedger.fromQueue(queue("BLOCKED"), 1L).size)
        assertEquals(0, PhotoOutcomeLedger.fromQueue(queue("FAILED"), 1L).size)
        assertEquals(0, PhotoOutcomeLedger.fromQueue(queue("PENDING"), 1L).size)
        assertEquals(1, PhotoOutcomeLedger.fromQueue(queue("DONE"), 1L).size)
        assertEquals("SKIPPED", PhotoOutcomeLedger.fromQueue(queue("SKIPPED"), 1L).single().state)
    }

    @Test fun batchOutcomesAreScopedToIndividualSourcePages() {
        val secondUrl = "https://example.org/gallery/two"
        val other = media.copy(url = "https://cdn.example.org/two.jpg",
            name = "Two.jpg")
        val batch = queue(source = "").copy(entries = listOf(
            QueueStore.Entry(media, "DONE", galleryUrl = page),
            QueueStore.Entry(other, "SKIPPED", galleryUrl = secondUrl)
        ))
        val records = PhotoOutcomeLedger.fromQueue(batch, 3L)
        assertEquals(2, records.size)
        assertEquals(setOf(media.url), PhotoOutcomeLedger.matchedUrls(
            records, page, batch.target, batch.root, batch.account,
            listOf(media, other), null))
        assertEquals(setOf(other.url), PhotoOutcomeLedger.matchedUrls(
            records, secondUrl, batch.target, batch.root, batch.account,
            listOf(media, other), null))
        assertEquals(setOf(media.url), PhotoOutcomeLedger.matchedUrls(
            emptyList(), page, batch.target, batch.root, batch.account,
            listOf(media, other), batch))
    }

    @Test fun storedMetadataContainsNoPrivateFileNamesUrlsOrAccount() {
        val entries = PhotoOutcomeLedger.fromQueue(queue(), 1L)
        val raw = PhotoOutcomeLedger.encode(entries)
        assertFalse(raw.contains(media.name))
        assertFalse(raw.contains(media.url))
        assertFalse(raw.contains(page))
        assertFalse(raw.contains("test@example.org"))
        assertEquals(entries, PhotoOutcomeLedger.decode(raw))
    }

    @Test fun sameNameAcrossCdnUrlChangesStillWarnsButDoesNotClaimIdentity() {
        val records = PhotoOutcomeLedger.fromQueue(queue(), 1L)
        val found = PhotoOutcomeLedger.matchedUrls(
            records, page, "Mail WebDAV", "Models", "test@example.org",
            listOf(changedUrl), null
        )
        assertEquals(setOf(changedUrl.url), found)
    }

    @Test fun destinationAccountAndGalleryScopeDoNotCross() {
        val records = PhotoOutcomeLedger.fromQueue(queue(), 1L)
        for ((src, dest, root, account) in listOf(
            listOf("https://example.org/gallery/two", "Mail WebDAV", "Models", "test@example.org"),
            listOf(page, "Mail WebDAV", "Other", "test@example.org"),
            listOf(page, "Mail WebDAV", "Models", "other@example.org"),
            listOf(page, "Папка Android", "content://test/tree/1", "")
        )) {
            assertTrue(PhotoOutcomeLedger.matchedUrls(
                records, src, dest, root, account, listOf(media), null
            ).isEmpty())
        }
    }

    @Test fun matchingCurrentQueueWorksWithoutPersistingLegacyHistory() {
        assertEquals(setOf(media.url), PhotoOutcomeLedger.matchedUrls(
            emptyList(), page, "Mail WebDAV", "Models", "test@example.org",
            listOf(media), queue("DONE")
        ))
    }

    @Test fun notMarkingUnknownLegacyGalleryAsKnown() {
        val missingPage = queue(source = "")
        assertTrue(PhotoOutcomeLedger.fromQueue(missingPage, 1L).isEmpty())
        assertTrue(PhotoOutcomeLedger.matchedUrls(
            emptyList(), page, "Mail WebDAV", "Models", "test@example.org",
            listOf(media), missingPage
        ).isEmpty())
    }

    @Test fun contentHashesArePersistedWithoutDroppingLegacyRecords() {
        val hash = "c".repeat(64)
        val completed = queue().copy(entries = listOf(
            QueueStore.Entry(media, "DONE", contentSha256 = hash)
        ))
        val records = PhotoOutcomeLedger.fromQueue(completed, 7L)
        assertEquals(hash, records.single().contentSha256)
        val parsed = PhotoOutcomeLedger.decode(PhotoOutcomeLedger.encode(records))
        assertEquals(records, parsed)

        val legacy = PhotoOutcomeLedger.decode(
            """[{"token":"${records.single().token}","state":"DONE","at":1}]"""
        )
        assertEquals("", legacy.single().contentSha256)
    }

    @Test fun missingHashFromLegacySnapshotCannotEraseKnownHash() {
        val hash = "d".repeat(64)
        val completed = queue().copy(entries = listOf(
            QueueStore.Entry(media, "DONE", contentSha256 = hash)
        ))
        val first = PhotoOutcomeLedger.fromQueue(completed, 10L)
        val remerged = PhotoOutcomeLedger.merge(first, queue("DONE"), 20L)
        assertEquals(hash, remerged.single().contentSha256)
        assertEquals(10L, remerged.single().at)
    }

    @Test fun newContentHashReplacesOldHashOfSameNamedPhoto() {
        val first = queue().copy(entries = listOf(
            QueueStore.Entry(media, "DONE", contentSha256 = "a".repeat(64))
        ))
        val second = queue().copy(entries = listOf(
            QueueStore.Entry(media, "DONE", contentSha256 = "b".repeat(64))
        ))
        val merged = PhotoOutcomeLedger.merge(PhotoOutcomeLedger.fromQueue(first, 1L), second, 2L)
        assertEquals("b".repeat(64), merged.single().contentSha256)
    }

    @Test fun invalidHashInLedgerFailsClosed() {
        val token = PhotoOutcomeLedger.fromQueue(queue(), 1L).single().token
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            PhotoOutcomeLedger.decode(
                """[{"token":"$token","state":"DONE","at":1,"contentSha256":"garbage"}]"""
            )
        }
    }

    @Test fun duplicateRecordsMergeByTokenAndPreserveLatestOutcome() {
        val original = PhotoOutcomeLedger.fromQueue(queue("DONE"), 10L)
        val next = PhotoOutcomeLedger.merge(original, queue("SKIPPED"), 20L)
        assertEquals(1, next.size)
        assertEquals("SKIPPED", next.single().state)
        assertEquals(20L, next.single().at)
    }

    @Test fun corruptLedgerCannotSilentlyBeTreatedAsEmpty() {
        for (raw in listOf("{broken", "[{\"token\":\"wrong\",\"state\":\"DONE\",\"at\":1}]")) {
            try {
                PhotoOutcomeLedger.decode(raw)
                fail("Corrupt ledger must be rejected")
            } catch (_: IllegalArgumentException) {
            } catch (_: org.json.JSONException) {
            }
        }
    }

    @Test fun duplicateFilenamesInOneBatchAreRejectedBeforeAnyTransfer() {
        assertEquals(setOf("photo 01.jpg"), PhotoOutcomeLedger.duplicateNames(
            listOf(media, changedUrl, media.copy(name = "Other.jpg"))
        ))
        assertTrue(PhotoOutcomeLedger.duplicateNames(
            listOf(media, media.copy(name = "Video.jpg", kind = Kind.VIDEO))
        ).isEmpty())
    }

    @Test fun videosDoNotBecomePhotoEvidence() {
        assertNull(PhotoOutcomeLedger.fingerprint(page, "Mail WebDAV", "Models", "",
            media.copy(kind = Kind.VIDEO)))
    }
}
