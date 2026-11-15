package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test

class MediaResultsTest {
    private fun media(n: Int, source: String = "Изображение (качество не подтверждено)") =
        Media("https://cdn.example.com/" + n + "_w400.jpg", n.toString() + "_w400.jpg", Kind.PHOTO, source)

    @Test fun limitsLargeResultsToThirtyWithoutLosingData() {
        val all = (0 until 300).map(::media)
        assertEquals(300, all.size)
        assertEquals(30, MediaResults.page(all, MediaResults.PAGE_SIZE).size)
        assertEquals(60, MediaResults.page(all, 60).size)
        assertEquals(300, MediaResults.page(all, 10_000).size)
        assertEquals(0, MediaResults.page(all, -1).size)
    }

    @Test fun recommendedViewShowsOnlyThePreferredPhotoVariant() {
        val first = media(0, "Ссылка")
        val thumbnail = media(1, "Изображение (качество не подтверждено)")
        val full = media(2, "Ссылка")
        val all = listOf(first, thumbnail, full)
        assertEquals(listOf(first, full), MediaResults.visible(all, false, setOf(first.url, full.url)))
        assertEquals(all, MediaResults.visible(all, true, setOf(first.url, full.url)))
        assertEquals(1, MediaResults.extraCount(all, setOf(first.url, full.url)))
    }

    @Test fun noRecommendationFallsBackToAllPhotos() {
        val all = listOf(media(1), media(2))
        assertEquals(all, MediaResults.visible(all, false, emptySet()))
        assertEquals(0, MediaResults.extraCount(all, emptySet()))
    }

    @Test fun videoItemsNeverAppearInPhotoResults() {
        val photo = media(1)
        val video = Media("https://cdn.example.com/clip.mp4", "clip.mp4", Kind.VIDEO, "Видео")
        val all = listOf(photo, video)
        assertEquals(listOf(photo), MediaResults.visible(all, true, setOf(photo.url, video.url)))
        assertEquals(listOf(photo), MediaResults.visible(all, false, setOf(photo.url, video.url)))
    }

    @Test fun hiddenAlternativesAreCountedWithoutClearingSelection() {
        val original = media(1)
        val alternate = media(2)
        val all = listOf(original, alternate)
        val selected = setOf(original.url, alternate.url)
        val recommended = MediaResults.visible(all, false, setOf(original.url))
        assertEquals(1, MediaResults.hiddenSelectionCount(selected, recommended))
        assertEquals(0, MediaResults.hiddenSelectionCount(selected, all))
        assertEquals(0, MediaResults.hiddenSelectionCount(emptySet(), recommended))
        assertEquals(2, selected.size)
    }

    @Test fun galleryWithThreeHundredVariantsPagesSafely() {
        val all = (0 until 300).map(::media)
        val recommended = all.take(15)
        val selected = setOf(all[0].url, all[90].url, all[299].url)
        val firstPage = MediaResults.page(all, MediaResults.PAGE_SIZE)
        val secondPage = MediaResults.page(all, MediaResults.PAGE_SIZE * 2)
        assertEquals(15, recommended.size)
        assertEquals(30, firstPage.size)
        assertEquals(60, secondPage.size)
        assertEquals(300, all.size)
        assertEquals(2, MediaResults.hiddenSelectionCount(selected, recommended))
        // Pagination itself must not be described as "hidden alternatives".
        assertEquals(0, MediaResults.hiddenSelectionCount(selected, all))
        assertTrue(selected.contains(all[299].url))
        assertEquals(all.take(30), firstPage)
    }

    @Test fun pagingNeverMutatesSelection() {
        val all = (0 until 300).map(::media)
        val selected = setOf(all[1].url, all[290].url)
        val page = MediaResults.page(all, 30)
        assertEquals(2, selected.size)
        assertEquals(30, page.size)
        assertTrue(selected.contains(all[290].url))
    }
}
