package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test

class QualitySelectorTest {
    private fun photo(name: String, source: String = "Ссылка") =
        Media("https://cdn.example.org/gallery/" + name, name, Kind.PHOTO, source)

    @Test fun prefers1800WhenBothExplicitlyLinked() {
        val items = listOf(photo("0002-01_1200.jpg"), photo("0002-01_1800.jpg"))
        assertEquals(setOf(items[1].url), QualitySelector.recommended(items))
    }

    @Test fun thumbnailDoesNotReplaceFullImage() {
        val full = photo("0002-01_1200.jpg")
        val thumb = photo("0002-01_w400.jpg", "Изображение (качество не подтверждено)")
        assertEquals(setOf(full.url), QualitySelector.recommended(listOf(full, thumb)))
    }

    @Test fun ignoresOtherUnrelatedPreviewGroups() {
        val gallery = photo("0002-01_1200.jpg")
        val unrelated = photo("other_w400.jpg", "Изображение (качество не подтверждено)")
        assertEquals(setOf(gallery.url), QualitySelector.recommended(listOf(gallery, unrelated)))
    }

    @Test fun doesNotMergePhotosFromDifferentDirectories() {
        val one = photo("0002-01_1200.jpg")
        val other = one.copy(url = "https://cdn.example.org/another/0002-01_1800.jpg", name = "0002-01_1800.jpg")
        assertEquals(2, QualitySelector.recommended(listOf(one, other)).size)
    }

    @Test fun srcsetExplicitLargerVariantCanWin() {
        val anchor = photo("0002-01_1200.jpg")
        val large = photo("0002-01_1800.jpg", "srcset 1800w (размер из HTML, не проверен)")
        assertEquals(setOf(large.url), QualitySelector.recommended(listOf(anchor, large)))
    }

    @Test fun unknownAnchorBeatsSmallPreview() {
        val main = photo("opening-up.jpg")
        val preview = photo("opening-up_w400.jpg", "Изображение (качество не подтверждено)")
        assertEquals(setOf(main.url), QualitySelector.recommended(listOf(main, preview)))
    }

    @Test fun noLinksCanUseSrcset() {
        val previews = listOf(
            photo("a_w400.jpg", "srcset 400w (размер из HTML, не проверен)"),
            photo("a_1200.jpg", "srcset 1200w (размер из HTML, не проверен)")
        )
        assertEquals(setOf(previews[1].url), QualitySelector.recommended(previews))
    }

    @Test fun neverInventsUrls() {
        val input = listOf(photo("0002-01_1200.jpg"))
        assertTrue(QualitySelector.recommended(input).all { target -> input.any { it.url == target } })
    }
}
