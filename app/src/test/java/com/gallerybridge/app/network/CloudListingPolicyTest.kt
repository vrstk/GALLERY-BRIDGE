package com.gallerybridge.app.network

import org.junit.Assert.*
import org.junit.Test

class CloudListingPolicyTest {
    private fun list(vararg entries: String): String =
        """{"files":[${entries.joinToString(",")}]}"""

    @Test fun aSingleExistingFileIsSkippedOnlyWhenNotKnownToBeEmpty() {
        val normal = list("""{"id":"a","mimeType":"image/jpeg","size":"2048"}""")
        assertTrue(CloudListingPolicy.existingFile(normal, "photo.jpg"))
        assertFalse(CloudListingPolicy.existingFile(list(), "photo.jpg"))
        val unknownSize = list("""{"id":"a","mimeType":"image/jpeg"}""")
        assertTrue(CloudListingPolicy.existingFile(unknownSize, "photo.jpg"))
    }

    @Test fun zeroByteFileIsNotSilentlySkipped() {
        val empty = list("""{"id":"a","mimeType":"image/jpeg","size":"0"}""")
        val error = assertThrows(IllegalArgumentException::class.java) {
            CloudListingPolicy.existingFile(empty, "photo.jpg")
        }
        assertTrue(error.message.orEmpty().contains("0 байт"))
    }

    @Test fun ambiguousFilesAndFoldersStopRatherThanPickTheFirstOne() {
        val duplicateFiles = list(
            """{"id":"a","mimeType":"image/jpeg","size":"20"}""",
            """{"id":"b","mimeType":"image/jpeg","size":"20"}"""
        )
        assertThrows(IllegalArgumentException::class.java) {
            CloudListingPolicy.existingFile(duplicateFiles, "photo.jpg")
        }
        val duplicateFolders = list(
            """{"id":"a","mimeType":"application/vnd.google-apps.folder"}""",
            """{"id":"b","mimeType":"application/vnd.google-apps.folder"}"""
        )
        assertThrows(IllegalArgumentException::class.java) {
            CloudListingPolicy.folderIdOrNull(duplicateFolders, "Gallery")
        }
    }

    @Test fun foldersMustBeFoldersAndFileNamesMustNotBeFolders() {
        val folder = list("""{"id":"a","mimeType":"application/vnd.google-apps.folder"}""")
        assertEquals("a", CloudListingPolicy.folderIdOrNull(folder, "Gallery"))
        assertThrows(IllegalArgumentException::class.java) {
            CloudListingPolicy.existingFile(folder, "Gallery")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CloudListingPolicy.folderIdOrNull(
                list("""{"id":"a","mimeType":"image/jpeg"}"""), "Gallery")
        }
    }

    @Test fun incompleteAndPaginatedSearchesAreNeverTreatedAsAbsence() {
        val incomplete = """{"incompleteSearch":true,"files":[]}"""
        val paginated = """{"nextPageToken":"page2","files":[]}"""
        assertThrows(IllegalArgumentException::class.java) {
            CloudListingPolicy.existingFile(incomplete, "photo.jpg")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CloudListingPolicy.folderIdOrNull(paginated, "Gallery")
        }
    }

    @Test fun driveQueriesEscapeBackslashAndApostrophe() {
        val query = CloudListingPolicy.searchQuery("root'\\value", "A'B\\C", true)
        assertTrue(query.contains("root\\'\\\\value"))
        assertTrue(query.contains("A\\'B\\\\C"))
        assertTrue(query.contains("application/vnd.google-apps.folder"))
    }
}
