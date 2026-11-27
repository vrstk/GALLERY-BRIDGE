package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test

class TransferTargetsTest {
    @Test fun onlyMailAndSystemFolderRemainAvailable() {
        assertTrue(TransferTargets.isSupported("Mail WebDAV"))
        assertTrue(TransferTargets.isSupported("Папка Android"))
        assertFalse(TransferTargets.isSupported("Google Drive"))
        assertFalse(TransferTargets.isSupported(""))
        assertFalse(TransferTargets.isSupported("Google Drive API"))
    }

    @Test fun resumeRequiresTheRecordedMailAccount() {
        TransferTargets.requireQueueCredentials(TransferTargets.MAIL, "owner@example.com",
            " owner@example.com ", "app-password")
        assertThrows(IllegalArgumentException::class.java) {
            TransferTargets.requireQueueCredentials(TransferTargets.MAIL, "owner@example.com",
                "other@example.com", "app-password")
        }
    }

    @Test fun unknownLegacyAccountAndMissingCredentialsAreBlocked() {
        assertThrows(IllegalArgumentException::class.java) {
            TransferTargets.requireQueueCredentials(TransferTargets.MAIL, "", "owner@example.com", "pass")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransferTargets.requireQueueCredentials(TransferTargets.MAIL, "owner@example.com", "", "pass")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransferTargets.requireQueueCredentials(TransferTargets.MAIL, "owner@example.com", "owner@example.com", "")
        }
    }

    @Test fun systemFolderDoesNotRequireMailCredentials() {
        TransferTargets.requireQueueCredentials(TransferTargets.FOLDER, "", "", "")
    }

    @Test fun oldGoogleApiPreferenceFallsBackToSystemFolder() {
        assertEquals("Папка Android", TransferTargets.restore("Google Drive"))
        assertEquals("Папка Android", TransferTargets.restore(null))
        assertEquals("Mail WebDAV", TransferTargets.restore("Mail WebDAV"))
    }
}
