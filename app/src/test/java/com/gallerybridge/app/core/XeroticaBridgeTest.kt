package com.gallerybridge.app.core

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class XeroticaBridgeTest {
    private val elite = "https://www.elitebabes.com/example-video/"
    private val watch = "https://www.xerotica.com/video/example-video-41839.html"

    @Test fun explicitArticleLinkOnly() {
        val html = """<main><article><div class="post-content">
            <a href="$watch">Смотреть публикацию</a>
            </div></article></main>
            <aside><a href="https://www.xerotica.com/video/unrelated-1.html">Другое видео</a></aside>"""
        assertEquals(watch, XeroticaBridge.externalPage(Jsoup.parse(html, elite), elite))
        assertNull(XeroticaBridge.externalPage(Jsoup.parse(html, elite),
            "https://other.example/video"))
    }

    @Test fun rejectionOfGuessingAndMixedTargets() {
        assertFalse(XeroticaBridge.acceptsWatchPage(
            "https://xerotica.com/models/alisa"))
        assertFalse(XeroticaBridge.acceptsWatchPage(
            "http://www.xerotica.com/video/example-11.html"))
        val html = """<main><article>
            <a href="https://www.xerotica.com/video/one-1.html">A</a>
            <a href="https://www.xerotica.com/video/two-2.html">B</a>
            </article></main>"""
        assertNull(XeroticaBridge.externalPage(Jsoup.parse(html, elite), elite))
    }

    @Test fun posterAndThreeExactVideoVariantsAreIndependentOfCatalogCover() {
        val page = watch
        val html = """<main><figure class="stage media-player">
            <video id="thisPlayer" poster="https://cdn.xerotica.com/posters/p.jpg">
              <source type="video/mp4" title="720p"
                 src="https://cdn-media.xerotica.com/movie_720.mp4?ri=2000k&amp;rs=400k"/>
              <source type="video/mp4" title="360p"
                 src="https://cdn-media.xerotica.com/movie_360.mp4?ri=500k"/>
              <source type="video/mp4" title="180p"
                 src="https://cdn-media.xerotica.com/movie_180.mp4?ri=250k"/>
            </video></figure></main>"""
        val doc = Jsoup.parse(html, page)
        assertEquals("https://cdn.xerotica.com/posters/p.jpg",
            PrimaryVideoSources.poster(doc, page))
        val variants = PrimaryVideoSources.extract(doc, page)
        assertEquals(3, variants.size)
        assertEquals("https://cdn-media.xerotica.com/movie_720.mp4?ri=2000k&rs=400k",
            variants.first().url)
    }
}
