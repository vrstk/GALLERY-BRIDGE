package com.gallerybridge.app.core

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class PrimaryVideoSourcesTest {
    private val page = "https://www.xerotica.com/video/leona-mia-has-an-amazing-figure-and-she-is-not-shy-to-show-it-41839.html"

    // Minimal reproduction of the actual primary <video id="thisPlayer"> in
    // the supplied MHT; unrelated links mirror the previous scan failure.
    private val html = """
        <html><head><title>Leona Mia</title></head><body><main>
        <figure class="stage media-player"><video id="thisPlayer"
          src="https://cdn-media.xerotica.com/videos/5/f/b/5/1/5fb51e6502847_720.mp4?ri=2000k&amp;rs=400k">
          <source src="https://cdn-media.xerotica.com/videos/5/f/b/5/1/5fb51e6502847_720.mp4?ri=2000k&amp;rs=400k" title="720p">
          <source src="https://cdn-media.xerotica.com/videos/5/f/b/5/1/5fb51e6502847.mp4?ri=500k&amp;rs=200k" title="360p">
          <source src="https://cdn-media.xerotica.com/videos/5/f/b/5/1/5fb51e6502847_180.mp4?ri=250k&amp;rs=100k" title="180p">
        </video></figure></main>
        <aside><a href="https://cdn-media.xerotica.com/videos/other.mp4">Recommended</a></aside>
        </body></html>
    """

    @Test fun exactPlayerOnlyNotRecommendations() {
        val doc = Jsoup.parse(html, page)
        val media = PrimaryVideoSources.extract(doc, page)
        assertEquals(3, media.size)
        assertEquals(listOf("5fb51e6502847_720.mp4", "5fb51e6502847.mp4",
            "5fb51e6502847_180.mp4"), media.map { it.name })
        assertTrue(media.all { it.kind == Kind.VIDEO && it.source == PrimaryVideoSources.SOURCE })
        assertTrue(media.none { it.url.contains("other.mp4") })
    }

    @Test fun exactQueryAndPageContextPreserved() {
        val media = PrimaryVideoSources.extract(Jsoup.parse(html, page), page)
        assertEquals("https://cdn-media.xerotica.com/videos/5/f/b/5/1/5fb51e6502847_720.mp4?ri=2000k&rs=400k",
            media.first().url)
        assertEquals(page, media.first().referrer)
    }

    @Test fun posterIsJpgAndPreservesExactQuery() {
        val video = "<figure class='stage media-player'><video id='thisPlayer' " +
            "poster='https://cdn-media.xerotica.com/poster/a.jpg?token=example'>" +
            "<source src='https://cdn-media.xerotica.com/videos/a.mp4?ri=2000k&amp;rs=400k'>" +
            "</video></figure>"
        val doc = Jsoup.parse(video, page)
        assertEquals("https://cdn-media.xerotica.com/poster/a.jpg?token=example",
            PrimaryVideoSources.posterUrl(doc, page))
        assertEquals("https://cdn-media.xerotica.com/videos/a.mp4?ri=2000k&rs=400k",
            PrimaryVideoSources.extract(doc, page).single().url)
        val invalid = Jsoup.parse(video.replace("https://cdn-media.xerotica.com/poster/a.jpg",
            "http://cdn-media.xerotica.com/poster/a.jpg"), page)
        assertNull(PrimaryVideoSources.posterUrl(invalid, page))
    }

    @Test fun plainElitePlayerRetainsThreeSourcesAndItsOwnPoster() {
        val publication = "https://www.elitebabes.com/example-44189"
        val mediaRoot = "https://media.elitebabes.com/video/"
        val poster = "https://cdn.elitebabes.com/example.jpg"
        val doc = Jsoup.parse("""<aside><video src="${mediaRoot}suggested.mp4"></video></aside>
            <main><video poster="$poster">
              <source src="${mediaRoot}a.mp4?q=1&amp;r=2" type="video/mp4">
              <source src="${mediaRoot}b.mp4?q=2" type="video/mp4">
              <source src="${mediaRoot}c.mp4?q=3" type="video/mp4">
            </video></main>""", publication)
        assertTrue(PrimaryVideoSources.isVideoPage(doc, publication))
        val sources = PrimaryVideoSources.extract(doc, publication)
        assertEquals(listOf("${mediaRoot}a.mp4?q=1&r=2", "${mediaRoot}b.mp4?q=2",
            "${mediaRoot}c.mp4?q=3"), sources.map { it.url })
        assertEquals(poster, PrimaryVideoSources.poster(doc, publication))
        doc.select("main").remove()
        assertFalse(PrimaryVideoSources.isVideoPage(doc, publication))
    }

    @Test fun emptyPlayerDoesNotGuessUrls() {
        val doc = Jsoup.parse("<html><main><video id='thisPlayer'></video></main></html>", page)
        assertTrue(PrimaryVideoSources.isVideoPage(doc, page))
        assertTrue(PrimaryVideoSources.extract(doc, page).isEmpty())
    }
}
