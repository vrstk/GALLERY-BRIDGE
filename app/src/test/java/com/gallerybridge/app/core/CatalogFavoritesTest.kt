package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test

class CatalogFavoritesTest {
    @Test fun changesOnlyTheSelectedNormalizedLabel() {
        val first = GalleryCatalogModels.key("  Example  Model ")
        val second = GalleryCatalogModels.key("Another")
        val previous = setOf(second)
        val added = CatalogFavorites.change(previous, first, true)
        assertEquals(setOf(second, first), added)
        assertEquals(setOf(second), previous)
        assertEquals(added, CatalogFavorites.change(added, first, true))
        assertEquals(previous, CatalogFavorites.change(added, first, false))
    }

    @Test fun unknownAndNonCanonicalLabelsCannotBeAdded() {
        assertFalse(CatalogFavorites.canFavorite(GalleryCatalogModels.key("")))
        assertFalse(CatalogFavorites.canFavorite(" Example "))
        assertThrows(IllegalArgumentException::class.java) {
            CatalogFavorites.change(emptySet(), "", true)
        }
    }

    @Test fun fullFavoritesNeverEvictExistingLabels() {
        val full = (1..CatalogFavorites.LIMIT).map { "model $it" }.toSet()
        assertThrows(IllegalArgumentException::class.java) {
            CatalogFavorites.change(full, "new model", true)
        }
        assertEquals(full, CatalogFavorites.change(full, "model 1", true))
        assertEquals(CatalogFavorites.LIMIT - 1,
            CatalogFavorites.change(full, "model 1", false).size)
    }
}
