package com.gallerybridge.app.network

import org.junit.Assert.*
import org.junit.Test

class SafFolderAccessTest {
    private val folder = "content://documents/tree/account%3AModels"

    @Test fun onlyExactPersistedReadWriteGrantIsAccepted() {
        val read = SafFolderAccess.Grant(folder, true, false)
        val write = SafFolderAccess.Grant(folder, false, true)
        assertFalse(SafFolderAccess.hasReadWriteGrant(folder, listOf(read, write)))
        assertTrue(SafFolderAccess.hasReadWriteGrant(folder,
            listOf(SafFolderAccess.Grant(folder, true, true))))
        assertFalse(SafFolderAccess.hasReadWriteGrant(folder,
            listOf(SafFolderAccess.Grant("$folder/subfolder", true, true))))
        assertFalse(SafFolderAccess.hasReadWriteGrant("", listOf(read, write)))
        assertFalse(SafFolderAccess.hasReadWriteGrant(folder, emptyList()))
    }

    @Test fun revokedOrDifferentProviderCannotResumeSavedFolder() {
        val grantedOther = SafFolderAccess.Grant("content://another/tree/other", true, true)
        assertFalse(SafFolderAccess.hasReadWriteGrant(folder, listOf(grantedOther)))
        assertFalse(SafFolderAccess.hasReadWriteGrant(folder,
            listOf(SafFolderAccess.Grant(folder, false, false))))
    }
}
