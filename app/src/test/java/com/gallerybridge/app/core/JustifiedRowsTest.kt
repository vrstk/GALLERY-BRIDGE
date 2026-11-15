package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test

class JustifiedRowsTest {
    private fun photo(index: Int) = Media(
        "https://example.com/$index.jpg", "$index.jpg", Kind.PHOTO, "Ссылка"
    )

    @Test fun everyPhotoAppearsOnceAndOrderIsPreserved() {
        val images = (1..24).map(::photo)
        val sizes = images.associate { it.url to (if (it.name.startsWith("1")) 0.7f else 1.6f) }
        val rows = JustifiedRows.pack(images, sizes, 400f, 150f)
        assertEquals(images, rows.flatMap { it.photos })
        assertTrue(rows.all { it.height > 0f && it.widths.all { width -> width > 0f } })
    }

    @Test fun landscapeImagesAreWiderThanPortraits() {
        val left = photo(1)
        val right = photo(2)
        val rows = JustifiedRows.pack(listOf(left, right),
            mapOf(left.url to 0.6f, right.url to 1.8f),
            availableWidth = 420f, targetHeight = 145f)
        assertEquals(2, rows.first().photos.size)
        assertTrue(rows.first().widths[1] > rows.first().widths[0])
    }

    @Test fun incompleteFinalRowIsNotStretched() {
        val media = photo(1)
        val rows = JustifiedRows.pack(listOf(media),
            mapOf(media.url to 1.25f),
            availableWidth = 420f, targetHeight = 145f)
        assertEquals(1, rows.size)
        assertEquals(145f, rows.first().height, 0.001f)
        assertTrue(rows.first().widths.single() < 420f)
    }

    @Test fun invalidImageRatiosUseSafeFallback() {
        val media = photo(1)
        for (ratio in listOf(0f, Float.NaN, Float.POSITIVE_INFINITY, -3f)) {
            val rows = JustifiedRows.pack(listOf(media),
                mapOf(media.url to ratio), availableWidth = 360f)
            assertTrue(rows.first().widths.single().isFinite())
            assertTrue(rows.first().widths.single() > 0f)
        }
    }

    @Test fun emptyGalleryReturnsNoRows() {
        assertTrue(JustifiedRows.pack(emptyList(), emptyMap(), 350f).isEmpty())
    }
}
