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
}
