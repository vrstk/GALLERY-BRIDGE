package com.gallerybridge.app.ui

import org.junit.Assert.*
import org.junit.Test

class GalleryPreviewModeTest {
    @Test fun newOrUnknownPreferenceUsesExistingMosaicDefault() {
        assertEquals(GalleryPreviewMode.MOSAIC, GalleryPreviewMode.restore(null))
        assertEquals(GalleryPreviewMode.MOSAIC, GalleryPreviewMode.restore(""))
        assertEquals(GalleryPreviewMode.MOSAIC, GalleryPreviewMode.restore("untrusted"))
    }

    @Test fun eachDisplayModeRestoresSafely() {
        GalleryPreviewMode.entries.forEach { mode ->
            assertEquals(mode, GalleryPreviewMode.restore(mode.name))
        }
    }

    @Test fun textOnlyModeNeverPreloadsImages() {
        assertFalse(GalleryPreviewMode.LIST.preloadsImages)
        assertTrue(GalleryPreviewMode.MOSAIC.preloadsImages)
        assertTrue(GalleryPreviewMode.TILES.preloadsImages)
    }
}
