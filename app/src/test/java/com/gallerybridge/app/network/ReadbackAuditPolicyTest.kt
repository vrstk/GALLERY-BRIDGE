package com.gallerybridge.app.network

import com.gallerybridge.app.core.Kind
import com.gallerybridge.app.core.Media
import com.gallerybridge.app.transfer.QueueStore
import org.junit.Assert.*
import org.junit.Test

class ReadbackAuditPolicyTest {
    private val media = Media("https://example.com/a.jpg", "a.jpg", Kind.PHOTO, "test")
    private val digest = "a".repeat(64)
    private fun snapshot(target: String = "Mail WebDAV", state: String = "DONE",
                         account: String = "mail@example.com") =
        QueueStore.Snapshot(1L, "Model", "Gallery", "", target,
            "Models", account, listOf(QueueStore.Entry(media, state, contentSha256 = digest)))

    @Test fun readOnlyAuditAvailableOnlyForCompletedSupportedQueues() {
        assertTrue(ReadbackAuditPolicy.eligible(snapshot()))
        assertTrue(ReadbackAuditPolicy.eligible(snapshot(target = "Папка Android", state = "SKIPPED")))
        assertFalse(ReadbackAuditPolicy.eligible(snapshot(state = "FAILED")))
        assertFalse(ReadbackAuditPolicy.eligible(snapshot(state = "BLOCKED")))
        assertFalse(ReadbackAuditPolicy.eligible(snapshot(target = "Google Drive API")))
        assertFalse(ReadbackAuditPolicy.eligible(snapshot(state = "PENDING")))
    }

    @Test fun storedSourceDigestDoesNotRequireReDownloadingOriginal() {
        assertEquals(digest, ReadbackAuditPolicy.storedDigest(snapshot().entries.single()))
        assertNull(ReadbackAuditPolicy.storedDigest(QueueStore.Entry(media, "DONE")))
        assertNull(ReadbackAuditPolicy.storedDigest(
            QueueStore.Entry(media, "DONE", contentSha256 = "malformed")
        ))
    }

    @Test fun sameShaAndDeclaredSizeEstablishReadbackMatch() {
        assertTrue(ReadbackAuditPolicy.matches(digest, digest, 100L, 100L))
        assertTrue(ReadbackAuditPolicy.matches(digest, digest, 100L))
    }

    @Test fun hashMismatchTruncationAndAmbiguousSizeNeverMatch() {
        assertFalse(ReadbackAuditPolicy.matches(digest, "b".repeat(64), 100L, 100L))
        assertFalse(ReadbackAuditPolicy.matches(digest, digest, 9L, 100L))
        assertFalse(ReadbackAuditPolicy.matches(digest, digest, 0L, 0L))
        assertFalse(ReadbackAuditPolicy.matches("bad", digest, 100L))
    }

    @Test fun mailAuditMustUseOriginalAccountAndExplicitPassword() {
        val queue = snapshot()
        assertTrue(ReadbackAuditPolicy.authorizedAccount(queue, "mail@example.com", "app-password"))
        assertFalse(ReadbackAuditPolicy.authorizedAccount(queue, "other@example.com", "app-password"))
        assertFalse(ReadbackAuditPolicy.authorizedAccount(queue, "mail@example.com", ""))
        assertFalse(ReadbackAuditPolicy.authorizedAccount(queue, "", "app-password"))
        assertFalse(ReadbackAuditPolicy.authorizedAccount(
            snapshot(target = "Папка Android"), "mail@example.com", "app-password"
        ))
    }
    @Test fun readbackUsesExactSingleGalleryDestination() {
        val q = snapshot()
        val entry = q.entries.single()
        assertEquals(
            com.gallerybridge.app.core.Paths.folders(
                q.model, q.date, q.title, media.kind),
            ReadbackAuditPolicy.folders(q, entry)
        )
        assertEquals(
            com.gallerybridge.app.core.Paths.splitRoot(q.root) +
                com.gallerybridge.app.core.Paths.folders(
                    q.model, q.date, q.title, media.kind) + media.name,
            ReadbackAuditPolicy.remoteSegments(q, entry)
        )
    }

    @Test fun batchReadbackSeparatesTwoGalleriesWithSameFilename() {
        val first = QueueStore.Entry(media, "DONE", contentSha256 = digest,
            galleryUrl = "https://www.elitebabes.com/gallery-one/",
            gallerySourceId = "101", galleryModel = "Alice", galleryTitle = "Session",
            galleryDate = "2026-01-01")
        val second = first.copy(
            galleryUrl = "https://www.elitebabes.com/gallery-two/",
            gallerySourceId = "202", galleryModel = "Bob")
        val batch = snapshot().copy(model = "Wrong queue-level model",
            title = "Wrong queue-level title", date = "2026-11-24",
            root = "Archive/Models", sourceUrl = "",
            entries = listOf(first, second))
        val firstFolders = ReadbackAuditPolicy.folders(batch, first)
        val secondFolders = ReadbackAuditPolicy.folders(batch, second)
        assertNotEquals(firstFolders, secondFolders)
        assertTrue(firstFolders.joinToString("/").contains("101"))
        assertTrue(secondFolders.joinToString("/").contains("202"))
        assertTrue(firstFolders.joinToString("/").contains("Alice"))
        assertTrue(secondFolders.joinToString("/").contains("Bob"))
        assertFalse(firstFolders.joinToString("/").contains("Wrong queue-level"))
        val remote = ReadbackAuditPolicy.remoteSegments(batch, second)
        assertEquals(listOf("Archive", "Models"), remote.take(2))
        assertEquals("a.jpg", remote.last())
        assertEquals(secondFolders, remote.drop(2).dropLast(1))
        assertEquals(com.gallerybridge.app.transfer.QueueStore.foldersFor(second, batch),
            secondFolders)
    }

    @Test fun missingTransferTimeDigestCannotBeReconstructedFromCurrentSource() {
        val missing = snapshot().entries.single().copy(contentSha256 = "")
        assertNull(ReadbackAuditPolicy.storedDigest(missing))
        assertFalse(ReadbackAuditPolicy.matches(
            missing.contentSha256, digest, 10L))
        // Invalid old queue content hashes are also not accepted as evidence.
        assertNull(ReadbackAuditPolicy.storedDigest(
            missing.copy(contentSha256 = "not-a-sha256")))
    }

    @Test fun auditReadbackReportDoesNotClaimCloudSynchronization() {
        val report = IntegrityAudit.Result(
            total = 2, matched = 1, different = 0, unavailable = 0,
            oversized = 0, dimensions = emptyList(), issues = emptyList(),
            target = "Папка Android", missingDigest = 1
        ).summary()
        assertTrue(report.contains("нет исходного SHA-256: 1"))
        assertTrue(report.contains("Синхронизация с удалённым облаком не подтверждена"))
        assertTrue(report.contains("не считается доказательством"))
    }

}
