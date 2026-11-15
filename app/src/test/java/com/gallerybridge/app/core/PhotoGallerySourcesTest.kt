package com.gallerybridge.app.core

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class PhotoGallerySourcesTest {
    private val page = "https://www.elitebabes.com/leona-mia-enjoys-fingering-her-tasty-vagina-on-the-couch-46853"
    private val html = """
        <html><head><title>SEO title with unrelated text</title></head><body>
        <h1>Leona Mia in Sex Art set Class Act</h1>
        <a class="fancy" data-fancybox="gallery-01"
          href="https://cdn.elitebabes.com/content/201231/0027-01_1200.jpg"><img src="https://cdn.elitebabes.com/content/201231/0027-01_w400.jpg"></a>
        <a class="fancy" data-fancybox="gallery-01"
          href="https://cdn.elitebabes.com/content/201231/0027-02_1200.jpg"></a>
        <a class="fancy" data-fancybox="gallery-01"
          href="https://cdn.elitebabes.com/content/201231/0027-03_1800.jpg"></a>
        <aside>
        <a class="fancy" data-fancybox="gallery-02" href="https://cdn.elitebabes.com/related.jpg"></a>
        <a href="https://cdn.elitebabes.com/advertisement.jpg">Advertisement</a>
        </aside>
        </body></html>
    """

    @Test fun extractsMainGalleryOriginalsNotThumbsOrAds() {
        val result = PhotoGallerySources.extract(Jsoup.parse(html, page), page)!!
        assertEquals(3, result.size)
        assertEquals(listOf("0027-01_1200.jpg", "0027-02_1200.jpg", "0027-03_1800.jpg"),
            result.map { it.name })
        assertTrue(result.all { it.kind == Kind.PHOTO && it.source == "Ссылка" })
        assertFalse(result.any { it.url.contains("w400") || it.url.contains("related") })
    }

    @Test fun unrecognizedSiteFallsBackToGenericScanner() {
        assertNull(PhotoGallerySources.extract(Jsoup.parse(html, "https://example.org/"), "https://example.org/"))
    }

    @Test fun doesNotInventUrlsForPageWithoutMainGallery() {
        assertNull(PhotoGallerySources.extract(Jsoup.parse("<h1>Other page</h1>", page), page))
    }

    @Test fun groupsDoNotMixUnrelatedGalleryItems() {
        val doc = Jsoup.parse(html, page)
        val media = PhotoGallerySources.extract(doc, page)!!
        assertEquals(3, media.size)
        assertTrue(media.all { it.url.contains("/content/201231/") })
    }
}
