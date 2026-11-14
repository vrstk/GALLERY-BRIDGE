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

    @Test fun videoVariantsAreGroupedAndPreferNamed720p() {
        val low = Media("https://cdn.example.org/v/clip_180.mp4", "clip_180.mp4", Kind.VIDEO, "Видео")
        val high = low.copy(url = "https://cdn.example.org/v/clip_720.mp4", name = "clip_720.mp4")
        val plain = low.copy(url = "https://cdn.example.org/v/clip.mp4", name = "clip.mp4")
        assertEquals(QualitySelector.groupKey(low), QualitySelector.groupKey(high))
        assertEquals(QualitySelector.groupKey(high), QualitySelector.groupKey(plain))
        assertEquals(setOf(high.url), QualitySelector.recommended(listOf(low, plain, high)))
        assertTrue(QualitySelector.hint(high).contains("720p"))
    }

    @Test fun videoVariantsInDifferentFoldersRemainSeparate() {
        val high = Media("https://cdn.example.org/a/clip_720.mp4", "clip_720.mp4", Kind.VIDEO, "Видео")
        val low = high.copy(url = "https://cdn.example.org/b/clip_180.mp4", name = "clip_180.mp4")
        assertEquals(2, QualitySelector.recommended(listOf(high, low)).size)
    }

    @Test fun neverInventsUrls() {
        val input = listOf(photo("0002-01_1200.jpg"))
        assertTrue(QualitySelector.recommended(input).all { target -> input.any { it.url == target } })
    }
}
