package com.gallerybridge.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Catalog grouping must not alter persisted labels, URLs or transfer states. */
class GalleryCatalogModelsTest {
    private val a1 = CatalogGallery(
        "https://example.org/1", "  Leona  Mia ", "Первый", "2025-08-29", 10,
        "Mail WebDAV", "Завершено", 7, 7
    )
    private val a2 = CatalogGallery(
        "https://example.org/2", "leona mia", "Второй", "", 9
    )
    private val b = CatalogGallery(
        "https://example.org/3", "Alba", "Третий", "", 8
    )

    @Test fun combinesCaseAndWhitespaceButRetainsOriginalRecords() {
        val originals = listOf(a1, a2, b)
        val grouped = GalleryCatalogModels.group(originals)
        assertEquals(listOf("Alba", "Leona Mia"), grouped.map { it.label })
        assertEquals(listOf(1, 2), grouped.map { it.count })
        val selected = GalleryCatalogModels.select(grouped, "leona mia")
        assertEquals(listOf(a1, a2), selected)
        assertEquals(7, selected[0].completed)
        assertEquals("  Leona  Mia ", originals[0].model)
        assertEquals(listOf(a1, a2, b), originals)
    }

    @Test fun unknownNamesHaveOneSeparateGroupAndComeLast() {
        val blank = CatalogGallery("https://example.org/4", "   ", "Без модели", "", 7)
        val literal = CatalogGallery("https://example.org/5", "Без имени", "Явная подпись", "", 6)
        val groups = GalleryCatalogModels.group(listOf(blank, literal, b))
        assertEquals(3, groups.size)
        assertEquals("Без имени", groups.last().label)
        assertEquals(listOf(blank), groups.last().entries)
        assertFalse(groups.last().key == GalleryCatalogModels.key(literal.model))
        assertEquals(emptyList<CatalogGallery>(), GalleryCatalogModels.select(groups, "not-found"))
    }

    @Test fun sortByRecentAndCountIsStableAndReadOnly() {
        val newest = CatalogGallery("https://example.org/new", "Zeta", "Новая", "", 100)
        val blank = CatalogGallery("https://example.org/blank", "", "Без имени", "", 200)
        val entries = listOf(a1, a2, b, newest, blank)
        val groups = GalleryCatalogModels.group(entries)
        assertEquals(listOf("Zeta", "Leona Mia", "Alba", "Без имени"),
            GalleryCatalogModels.sorted(groups, GalleryCatalogModels.Sort.RECENT).map { it.label })
        assertEquals(listOf("Leona Mia", "Alba", "Zeta", "Без имени"),
            GalleryCatalogModels.sorted(groups, GalleryCatalogModels.Sort.COUNT).map { it.label })
        assertEquals(listOf("Alba", "Leona Mia", "Zeta", "Без имени"), groups.map { it.label })
        assertEquals(listOf(a1, a2), GalleryCatalogModels.select(groups, "leona mia"))
    }

    @Test fun acceptsFilteredInputWithoutReintroducingUnmatchedEntries() {
        val group = GalleryCatalogModels.group(listOf(a1, b))
        val selected = GalleryCatalogModels.select(group, "leona mia")
        assertEquals(listOf(a1), selected)
        assertTrue(selected.none { it.url == a2.url })
        assertEquals(emptyList<GalleryCatalogModels.Group>(), GalleryCatalogModels.group(emptyList()))
    }
}
