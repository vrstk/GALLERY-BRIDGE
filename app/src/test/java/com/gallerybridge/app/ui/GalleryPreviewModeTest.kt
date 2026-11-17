package com.gallerybridge.app.ui

import org.junit.Assert.*
import org.junit.Test

class GalleryPreviewModeTest {
    @Test fun legacyPreferencesNeverEnableTilesOrTextOnly() {
        listOf(null, "", "MOSAIC", "TILES", "LIST", "untrusted").forEach {
            assertEquals(GalleryPreviewMode.MOSAIC, GalleryPreviewMode.restore(it))
        }
    }

    @Test fun mosaicIsTheOnlyDisplayModeAndAlwaysPreloadsImages() {
        assertEquals(listOf(GalleryPreviewMode.MOSAIC), GalleryPreviewMode.entries)
        assertTrue(GalleryPreviewMode.MOSAIC.preloadsImages)
    }
}
