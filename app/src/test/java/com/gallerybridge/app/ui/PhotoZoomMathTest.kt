package com.gallerybridge.app.ui

import org.junit.Assert.*
import org.junit.Test

class PhotoZoomMathTest {
    @Test fun fitImageHasNoPanAndZoomUsesDisplayedImageDimensions() {
        assertEquals(PhotoZoomMath.Bounds(0f, 0f),
            PhotoZoomMath.bounds(1080f, 1920f, 1.5f, 1f))
        val landscapeInPortrait = PhotoZoomMath.bounds(1080f, 1920f, 1.5f, 2.5f)
        assertEquals(810f, landscapeInPortrait.x, 0.01f)
        assertEquals(0f, landscapeInPortrait.y, 0.01f)
    }

    @Test fun portraitInLandscapeAllowsPanAfterZoom() {
        val bounds = PhotoZoomMath.bounds(1920f, 1080f, 0.67f, 3f)
        assertTrue(bounds.x > 0f)
        assertTrue(bounds.y > 0f)
        assertEquals(PhotoZoomMath.Bounds(0f, 0f),
            PhotoZoomMath.bounds(1920f, 1080f, 0.67f, 1f))
    }

    @Test fun doubleTapFillsViewportWithSafeScaleLimits() {
        assertTrue(PhotoZoomMath.doubleTapScale(1920f, 1080f, 0.67f) >= 2f)
        assertTrue(PhotoZoomMath.doubleTapScale(1920f, 1080f, 0.1f)
            <= PhotoZoomMath.MAX_SCALE)
        assertEquals(2.5f, PhotoZoomMath.doubleTapScale(0f, 1080f, 1f), 0f)
        assertEquals(PhotoZoomMath.Bounds(0f, 0f),
            PhotoZoomMath.bounds(100f, 100f, Float.NaN, 3f))
    }
}
