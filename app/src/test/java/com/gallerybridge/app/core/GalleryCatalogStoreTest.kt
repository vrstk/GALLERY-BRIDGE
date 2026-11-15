package com.gallerybridge.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryCatalogStoreTest {
    private fun gallery(url: String, model: String, title: String, timestamp: Long) =
        CatalogGallery(url, model, title, "", timestamp)

    @Test fun equivalentHttpsLinksShareOneRecord() {
        val first = gallery("https://EXAMPLE.com/alba/", "Alba", "First", 10)
        val second = gallery("https://example.com/alba", "Alba", "New title", 20)
        val result = GalleryCatalogStore.merge(GalleryCatalogStore.merge(emptyList(), first), second)
        assertEquals(1, result.size)
        assertEquals("New title", result.single().title)
        assertEquals("https://example.com/alba", result.single().url)
    }

    @Test fun editingMetadataKeepsLastTransferStatus() {
        val completed = CatalogGallery("https://example.com/a", "A", "Old", "", 20,
            "Mail WebDAV", "Завершено", 12, 12)
        val edited = gallery("https://example.com/a", "A", "Edited", 30)
        val merged = GalleryCatalogStore.merge(listOf(completed), edited).single()
        assertEquals("Edited", merged.title)
        assertEquals("Mail WebDAV", merged.target)
        assertEquals("Завершено", merged.outcome)
        assertEquals(12, merged.completed)
    }

    @Test fun invalidAndHttpUrlsAreNotStored() {
        assertTrue(GalleryCatalogStore.merge(emptyList(), gallery("http://example.com/a", "A", "X", 2)).isEmpty())
        assertTrue(GalleryCatalogStore.merge(emptyList(), gallery("https://example.com/a", "A", "X", 2).copy(date = "2026-02-31")).isEmpty())
        assertFalse(GalleryCatalogStore.decode("""[{"url":"content://images/5"}]""").isNotEmpty())
    }

    @Test fun serializationSurvivesMalformedRowAndDeduplicates() {
        val data = """[{"url":"https://EXAMPLE.com/a/","model":"A","title":"Old","updatedAt":1},
          {"url":"content://local/5","title":"Should not appear"},
          {"url":"https://example.com/a","model":"A","title":"New","updatedAt":2}]"""
        val restored = GalleryCatalogStore.decode(data)
        assertEquals(1, restored.size)
        assertEquals("New", restored.single().title)
        assertEquals(restored, GalleryCatalogStore.decode(GalleryCatalogStore.encode(restored)))
    }

    @Test fun transferDoesNotOverwriteEditedLabels() {
        val old = CatalogGallery("https://example.com/alba", "Alba", "New title",
            "2026-01-04", 90, "Mail WebDAV", "Done", 4, 4)
        val incoming = CatalogGallery("https://example.com/alba", "Old model", "Old title",
            "2020-02-02", 100, "Google Drive", "Done", 8, 8)
        val updated = GalleryCatalogStore.merge(listOf(old), incoming).single()
        assertEquals("Alba", updated.model)
        assertEquals("New title", updated.title)
        assertEquals("2026-01-04", updated.date)
        assertEquals("Google Drive", updated.target)
        assertEquals(8, updated.completed)
    }

    @Test fun editedRecordCanClearDateAndKeepTransferResult() {
        val old = CatalogGallery("https://example.com/one", "Alba", "Old",
            "2026-01-04", 90, "Mail WebDAV", "Done", 4, 4)
        val updated = GalleryCatalogStore.merge(listOf(old),
            CatalogGallery(old.url, "Alba", "New", "", 110)).single()
        assertEquals("", updated.date)
        assertEquals("New", updated.title)
        assertEquals(4, updated.completed)
    }

    @Test fun catalogSearchFiltersAndSortsWithoutChangingRecords() {
        val entries = listOf(
            CatalogGallery("https://example.com/1", "Alba", "Studio", "", 5),
            CatalogGallery("https://example.com/2", "Zara", "Outdoor", "", 8,
                "Google Drive", "Done", 2, 2),
            CatalogGallery("https://example.com/3", "ALBA", "Evening", "", 10,
                "Mail WebDAV", "Done", 3, 3)
        )
        assertEquals(listOf("Evening", "Studio"),
            GalleryCatalogFilter.apply(entries, " alba ", false, false).map { it.title })
        assertEquals(listOf("Evening", "Outdoor"),
            GalleryCatalogFilter.apply(entries, "", false, true).map { it.title })
        assertEquals(listOf("Evening", "Studio", "Outdoor"),
            GalleryCatalogFilter.apply(entries, "", true, false).map { it.title })
        assertEquals(3, entries.size)
    }

    @Test fun savedMetadataDetectsChangesWithoutMutatingCatalog() {
        val record = CatalogGallery("https://EXAMPLE.com/a/", "Leona Mia",
            "Sea", "2021-06-18", 4, "Mail WebDAV", "Завершено", 12, 12)
        val saved = listOf(record)
        assertTrue(GalleryCatalogStore.contains(saved, "https://example.com/a"))
        assertTrue(GalleryCatalogStore.hasSavedFields(saved,
            "https://example.com/a", GalleryFields(" Leona Mia ", "Sea", "2021-06-18")))
        assertFalse(GalleryCatalogStore.hasSavedFields(saved,
            "https://example.com/a", GalleryFields("Leona Mia", "Different", "2021-06-18")))
        assertFalse(GalleryCatalogStore.hasSavedFields(saved,
            "https://example.com/other", GalleryFields("Leona Mia", "Sea", "2021-06-18")))
        assertEquals(12, saved.single().completed)
    }

    @Test fun boundedCatalogKeepsNewestRecords() {
        val records = (0..GalleryCatalogStore.LIMIT).fold(emptyList<CatalogGallery>()) { all, index ->
            GalleryCatalogStore.merge(all, gallery("https://example.com/" + index, "A", "T", index.toLong()))
        }
        assertEquals(GalleryCatalogStore.LIMIT, records.size)
        assertTrue(records.any { it.url == "https://example.com/" + GalleryCatalogStore.LIMIT })
        assertTrue(records.none { it.url == "https://example.com/0" })
    }
}
