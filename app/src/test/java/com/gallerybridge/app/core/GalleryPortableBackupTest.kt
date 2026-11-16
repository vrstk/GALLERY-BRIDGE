package com.gallerybridge.app.core

import com.gallerybridge.app.transfer.HistoryStore
import com.gallerybridge.app.transfer.PortableHistoryBackup
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GalleryPortableBackupTest {
    private val gallery = CatalogGallery("https://example.com/a", "Alba", "Studio",
        "2026-01-12", 123L, "Mail WebDAV", "Завершено", 12, 12)
    private val transfer = HistoryStore.Record(99, 321, "Alba", "Studio", "2026-01-12",
        "Mail WebDAV", 12, 12, 0, 0, "Завершено")

    @Test fun combinedBackupPreservesGalleryHistoryAndTheme() {
        val backup = GalleryPortableBackup.export(listOf(gallery), listOf(transfer), false)
        assertEquals(GalleryPortableBackup.Payload(listOf(gallery), listOf(transfer), false),
            GalleryPortableBackup.parse(backup))
        assertFalse(backup.contains("password"))
        assertFalse(backup.contains("treeUri"))
        assertFalse(backup.contains("account"))
    }

    @Test fun oldCatalogOnlyFileCanStillBeImported() {
        val older = GalleryCatalogBackup.export(listOf(gallery))
        assertEquals(GalleryPortableBackup.Payload(listOf(gallery), emptyList(), null),
            GalleryPortableBackup.parse(older))
    }

    @Test fun corruptionOrUnsupportedVersionIsRejected() {
        val backup = GalleryPortableBackup.export(listOf(gallery), listOf(transfer), true)
        assertThrows(IllegalArgumentException::class.java) {
            GalleryPortableBackup.parse(backup.replace("Studio", "Fake"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GalleryPortableBackup.parse(JSONObject(backup).put("version", 4).toString())
        }
    }

    @Test fun historyWithInvalidCountsCannotBeDecoded() {
        val invalid = PortableHistoryBackup.encode(listOf(transfer))
        invalid.getJSONObject(0).put("uploaded", 99)
        assertThrows(IllegalArgumentException::class.java) {
            PortableHistoryBackup.decode(invalid)
        }
    }

    @Test fun importHistoryNeverOverridesLocalCompletion() {
        val local = transfer.copy(title = "Local", updatedAt = 500)
        val incoming = listOf(transfer, transfer.copy(id = 100, title = "New", updatedAt = 200))
        val planned = PortableHistoryBackup.plan(listOf(local), incoming)
        assertEquals(1, planned.added)
        assertEquals(1, planned.alreadyPresent)
        assertEquals(0, planned.noSpace)
        assertEquals(local, planned.records.first { it.id == 99L })
        assertEquals(2, planned.records.size)
    }
}
