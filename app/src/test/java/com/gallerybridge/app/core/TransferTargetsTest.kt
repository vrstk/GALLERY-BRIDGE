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

    @Test fun oldGoogleApiPreferenceFallsBackToSystemFolder() {
        assertEquals("Папка Android", TransferTargets.restore("Google Drive"))
        assertEquals("Папка Android", TransferTargets.restore(null))
        assertEquals("Mail WebDAV", TransferTargets.restore("Mail WebDAV"))
    }
}
