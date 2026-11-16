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
    @Test fun ignoresLargerSidebarGalleryAndPrioritizesMainArticle() {
        val doc = Jsoup.parse("""
            <main><article class="post-content">
              <a data-fancybox="gallery-main" href="https://cdn.elitebabes.com/main/01_1800.jpg">full</a>
              <a data-fancybox="gallery-main" href="https://cdn.elitebabes.com/main/02_1800.jpg">full</a>
            </article></main>
            <aside class="related widget">
              <a data-fancybox="gallery-other" href="https://cdn.elitebabes.com/aside/01.jpg">related</a>
              <a data-fancybox="gallery-other" href="https://cdn.elitebabes.com/aside/02.jpg">related</a>
              <a data-fancybox="gallery-other" href="https://cdn.elitebabes.com/aside/03.jpg">related</a>
              <a data-fancybox="gallery-other" href="https://cdn.elitebabes.com/aside/04.jpg">related</a>
            </aside>
        """, page)
        val result = PhotoGallerySources.extract(doc, page)!!
        assertEquals(2, result.size)
        assertTrue(result.all { it.url.contains("/main/") })
    }

    @Test fun supportsFancyboxWithoutFancyClassAndDeduplicatesLinks() {
        val doc = Jsoup.parse("""
            <article>
              <a data-fancybox="gallery" href="/media/one_1800.jpg"><img src="/media/one_w400.jpg"></a>
              <a data-fancybox="gallery" href="/media/one_1800.jpg">duplicate</a>
              <a data-fancybox="gallery" href="/media/two_1800.jpg?token=opaque">full</a>
            </article>
        """, page)
        val results = PhotoGallerySources.extract(doc, page)!!
        assertEquals(2, results.size)
        assertEquals(listOf("one_1800.jpg", "two_1800.jpg"), results.map { it.name })
        assertTrue(results[1].url.endsWith("?token=opaque"))
    }

    @Test fun relatedOnlyContentFallsBackWithoutSelectingAnAdGallery() {
        val doc = Jsoup.parse("""
            <nav><a data-fancybox="gallery-nav" href="/unrelated.jpg">nav</a></nav>
            <div class="sponsored-content">
              <a data-fancybox="gallery-related" href="/advert.jpg">ad</a>
            </div>
            <footer><a data-fancybox="gallery-bottom" href="/banner.jpg">footer</a></footer>
        """, page)
        assertNull(PhotoGallerySources.extract(doc, page))
    }

    @Test fun mainArticleGroupWinsWhenSidebarIsNotAsideTag() {
        val doc = Jsoup.parse("""
            <div class="sidebar-widget">
              <a data-fancybox="gallery-large" href="/ad-1.jpg"></a>
              <a data-fancybox="gallery-large" href="/ad-2.jpg"></a>
              <a data-fancybox="gallery-large" href="/ad-3.jpg"></a>
            </div>
            <article><a data-fancybox="gallery-01" href="/real.jpg"></a></article>
        """, page)
        assertEquals(listOf("real.jpg"),
            PhotoGallerySources.extract(doc, page)!!.map { it.name })
    }

}
