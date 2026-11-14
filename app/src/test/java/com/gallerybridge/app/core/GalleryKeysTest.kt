package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test

class GalleryKeysTest {
    @Test fun sameUrlWithFragmentOrTrailingSlash() {
        assertTrue(GalleryKeys.same("https://WWW.ELITEBABES.COM/gallery-123/#top", "https://www.elitebabes.com/gallery-123"))
    }

    @Test fun separatePagesAndQueryValuesDoNotCollide() {
        assertFalse(GalleryKeys.same("https://example.com/gallery?id=1", "https://example.com/gallery?id=2"))
        assertFalse(GalleryKeys.same("https://example.com/gallery-one", "https://example.com/gallery-two"))
    }

    @Test fun nonHttpsAndMalformedLinksCannotBeSaved() {
        assertNull(GalleryKeys.key("http://example.com/a"))
        assertNull(GalleryKeys.key("not a link"))
    }

    @Test fun dateMustBeRealIsoDayOrEmpty() {
        assertTrue(GalleryKeys.validDate(""))
        assertTrue(GalleryKeys.validDate("2020-10-13"))
        assertFalse(GalleryKeys.validDate("2020-02-30"))
        assertFalse(GalleryKeys.validDate("2020/10/13"))
    }
}
