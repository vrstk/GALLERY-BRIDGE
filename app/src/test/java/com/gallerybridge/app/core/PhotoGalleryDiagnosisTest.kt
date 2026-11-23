package com.gallerybridge.app.core

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class PhotoGalleryDiagnosisTest {
    private val page = "https://www.elitebabes.com/example-publication/"

    @Test fun onlyPreviewsDoNotBecomeSources() {
        val doc = Jsoup.parse("""
            <main id="content"><article>
              <img src="/previews/one_masonry_400.jpg">
              <img src="/previews/two_w400.jpg">
              <aside><a data-fancybox="gallery" href="/ad.jpg">Advertisement</a></aside>
            </article></main>
        """, page)
        val result = PhotoGalleryDiagnosis.inspect(doc, page)
        assertEquals(0, result.originals)
        assertEquals(0, result.controls)
        assertEquals(2, result.previews)
        assertTrue(result.summary().contains("Миниатюры"))
    }

    @Test fun rejectedGroupExplainsThatLinksAreUnconfirmed() {
        val doc = Jsoup.parse("""
            <article><a data-fancybox="gallery" href="/photos/one_masonry_400.jpg">
              <img src="/photos/one_masonry_400.jpg">
            </a></article>
        """, page)
        val result = PhotoGalleryDiagnosis.inspect(doc, page)
        assertEquals(1, result.controls)
        assertEquals(0, result.originals)
        assertTrue(result.summary().contains("подтверждённые ссылки"))
    }

    @Test fun candidatesAreCountedWithoutClaimingValidDownloadedFiles() {
        val doc = Jsoup.parse("""
            <article><a data-fancybox="gallery" href="/photos/one_1800.jpg">One</a>
            <a data-fancybox="gallery" href="/photos/two_1800.jpg">Two</a></article>
        """, page)
        val result = PhotoGalleryDiagnosis.inspect(doc, page)
        assertEquals(2, result.originals)
        assertTrue(result.summary().contains("проверено при скачивании"))
    }

    @Test fun unrelatedHostCannotBeDiagnosedAsSupportedGallery() {
        val doc = Jsoup.parse(
            "<article><a data-fancybox='gallery' href='/one.jpg'>One</a></article>",
            "https://example.com/"
        )
        assertEquals(0, PhotoGalleryDiagnosis.inspect(doc, "https://example.com/").originals)
    }
}
