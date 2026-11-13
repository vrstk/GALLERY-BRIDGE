package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test

class PathsTest {
    @Test fun correctFolders() {
        assertEquals("♀️ Alba", Paths.folders("Alba", "", "Opening Up", Kind.PHOTO)[0])
        assertEquals("📷 Photo", Paths.folders("Alba", "", "Opening Up", Kind.PHOTO)[1])
        assertTrue(Paths.folders("Alba", "", "Opening Up", Kind.PHOTO)[2].startsWith("🍓 Без даты"))
    }
    @Test fun removesSlashes() { assertFalse(Paths.clean("A/B").contains("/")) }
}
