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

    @Test fun defaultViewShowsDirectAndRecommendedOnly() {
        val all = listOf(media(0), media(1, "Ссылка"), media(2))
        val visible = MediaResults.visible(all, false, setOf(all[2].url))
        assertEquals(listOf(all[1], all[2]), visible)
        assertEquals(all, MediaResults.visible(all, true, emptySet()))
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
