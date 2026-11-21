package com.gallerybridge.app.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class GalleryCatalogBackupTest {
    private val first = CatalogGallery("https://example.com/a", "Alba", "A", "2026-01-12", 20,
        "Mail WebDAV", "Завершено", 12, 12)
    private val second = CatalogGallery("https://example.com/b", "Leona", "B", "", 10)

    @Test fun fullRoundTripRetainsMetadataAndTransferStatus() {
        val exported = GalleryCatalogBackup.export(listOf(first, second))
        assertEquals(listOf(first, second), GalleryCatalogBackup.parse(exported))
        assertFalse(exported.contains("password"))
        assertEquals(exported, GalleryCatalogBackup.readLimited(
            ByteArrayInputStream(exported.toByteArray(Charsets.UTF_8))))
    }

    @Test fun malformedFilesAndChecksumMismatchesAreRejected() {
        val text = GalleryCatalogBackup.export(listOf(first))
        val tampered = text.replace("Alba", "Fake")
        assertThrows(IllegalArgumentException::class.java) { GalleryCatalogBackup.parse(tampered) }
        val wrongVersion = JSONObject(text).put("version", 99).toString()
        assertThrows(IllegalArgumentException::class.java) { GalleryCatalogBackup.parse(wrongVersion) }
        val wrongCount = JSONObject(text).put("count", 2).toString()
        assertThrows(IllegalArgumentException::class.java) { GalleryCatalogBackup.parse(wrongCount) }
        assertThrows(Exception::class.java) { GalleryCatalogBackup.parse("[]") }
    }

    @Test fun importPreservesExistingEditsAndTransfers() {
        val newer = first.copy(title = "Local edit", completed = 10, updatedAt = 90)
        val plan = GalleryCatalogBackup.plan(listOf(newer), listOf(first, second))
        assertEquals(1, plan.added)
        assertEquals(1, plan.alreadyPresent)
        assertEquals(0, plan.noSpace)
        assertEquals(newer, plan.records.first { it.url == first.url })
        assertEquals(second, plan.records.first { it.url == second.url })
    }

    @Test fun fullCatalogNeverDropsLocalRecords() {
        val full = (0 until GalleryCatalogStore.LIMIT).map { n ->
            first.copy(url = "https://example.com/$n", updatedAt = n.toLong())
        }
        val plan = GalleryCatalogBackup.plan(full, listOf(first, second))
        assertEquals(0, plan.added)
        assertEquals(2, plan.noSpace)
        assertEquals(full.size, plan.records.size)
    }

    @Test fun oversizedBackupIsRejected() {
        val tooLarge = ByteArray(GalleryCatalogBackup.MAX_BYTES + 1) { 65 }
        assertThrows(IllegalArgumentException::class.java) {
            GalleryCatalogBackup.readLimited(ByteArrayInputStream(tooLarge))
        }
    }
}
