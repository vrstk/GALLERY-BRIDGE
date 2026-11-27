package com.gallerybridge.app.core

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ModelListingSourcesTest {
    private val first = "https://www.elitebabes.com/model/example-model/sort/oldest/mpage/1"
    private val third = "https://www.elitebabes.com/model/example-model/sort/oldest/mpage/3"

    private fun card(link: String, title: String, date: String, video: Boolean = false): String =
        """<li><figure class="has-img">
             <span class="img-label"><span>$date</span>${if (video) "<i class='icon-play'></i>" else ""}</span>
             <a href="$link"><img src="https://cdn.elitebabes.com/masonry_400.jpg"></a>
             <div class="img-overlay"><p><a href="$link">$title</a></p></div>
           </figure></li>"""

    private fun html(cards: String, pages: String = "", loader: Boolean = true): String =
        """<html><head><title>Example Model (432 Galleries)</title></head>
           <body><main id="content">
           <article class="module-model"><h1>Example Model</h1></article>
           <ul class="list-gallery has-mobile-menu">$cards</ul>
           <div id="pagination_infinite">$pages</div>
           ${if (loader) "<div id='loader'></div><button id='load-more'>Load More</button>" else ""}
           </main></body></html>"""

    @Test fun mhtCardShapeSeparatesVideosFromPhotoPublicationLinks() {
        val cards = card("https://www.elitebabes.com/gallery-one/", "Gallery One", "2018-02-12") +
            card("https://www.elitebabes.com/gallery-two/", "Gallery Two", "2018-03-05") +
            card("https://www.elitebabes.com/video-example/", "Video", "2018-01-16", true)
        val pages = """<a class="pagination_infinite_active">1</a>
            <a href="/model/example-model/sort/oldest/mpage/2/">2</a>
            <a href="/model/example-model/sort/oldest/mpage/3/">3</a>"""
        val parsed = ModelListingSources.parse(Jsoup.parse(html(cards,pages),first),first)!!
        assertEquals(3, parsed.cardCount)
        assertEquals(2, parsed.photos.size)
        assertEquals(1, parsed.videos)
        assertEquals("2018-02-12", parsed.photos[0].date)
        assertEquals("Example Model", parsed.photos[0].model)
        assertEquals(setOf(1,2,3),parsed.pages.keys)
        assertEquals(432, parsed.advertisedCount)
        assertTrue(parsed.infiniteLoader)
        assertFalse(parsed.photos.any { it.url.contains("masonry_400") })
    }

    @Test fun onlyFirstTwentyOfNumberedPageMustRemainIncomplete() {
        val a=(1..20).joinToString("") {
            card("https://www.elitebabes.com/gallery-$it/", "Photo $it", "2018-02-12")
        }
        val b=(21..40).joinToString("") {
            card("https://www.elitebabes.com/gallery-$it/", "Photo $it", "2026-02-12")
        }
        val p1=ModelListingSources.parse(Jsoup.parse(html(a),first),first)!!
        val p3=ModelListingSources.parse(Jsoup.parse(html(b),third),third)!!
        val result=ModelListingSources.combine(listOf(p3,p1))
        assertEquals(40,result.photos.size)
        assertEquals(40,result.cards)
        assertEquals(2,result.loadedPages)
        assertTrue(result.incomplete)
    }

    @Test fun acceptsMoreThanTwentyAlreadyRenderedCards() {
        val cards=(1..135).joinToString("") {
            card("https://www.elitebabes.com/gallery-$it/", "Photo $it", "2025-01-01")
        }
        val found=ModelListingSources.parse(Jsoup.parse(html(cards),first),first)!!
        assertEquals(135,found.photos.size)
    }

    @Test fun duplicatePublicationsAreMergedByUrl() {
        val card=card("https://www.elitebabes.com/a/", "Photo", "2026-02-01")
        val p1=ModelListingSources.parse(Jsoup.parse(html(card),first),first)!!
        val p3=ModelListingSources.parse(Jsoup.parse(html(card),third),third)!!
        assertEquals(1,ModelListingSources.combine(listOf(p3,p1)).photos.size)
    }

    @Test fun maliciousAndUnrelatedLinksCannotBeImported() {
        val cards=card("https://bad.example.com/a/", "Bad", "2026-01-01")+
            card("http://www.elitebabes.com/a/", "HTTP", "2026-01-01")+
            card("https://www.elitebabes.com/video/something/", "Video", "2026-01-01")+
            card("https://www.elitebabes.com/ok/", "OK", "2026-02-29")+
            card("https://www.elitebabes.com/good/", "Good", "2026-02-28")
        val parsed=ModelListingSources.parse(Jsoup.parse(html(cards),first),first)!!
        assertEquals(2,parsed.photos.size)
        assertEquals("",parsed.photos[0].date)
        assertEquals("2026-02-28",parsed.photos[1].date)
        val foreign="""<a href="https://example.org/model/example-model/sort/oldest/mpage/2/">bad</a>
          <a href="/model/another-model/sort/oldest/mpage/3/">other</a>"""
        val page=ModelListingSources.parse(Jsoup.parse(html("",foreign),first),first)!!
        assertEquals(setOf(1),page.pages.keys)
    }

    @Test fun sourcePagesHaveStrictHostSchemeAndBounds() {
        assertTrue(ModelListingSources.accepts(first))
        assertTrue(ModelListingSources.accepts(third))
        assertTrue(ModelListingSources.accepts("https://www.elitebabes.com/model/example-model/sort/oldest/"))
        assertFalse(ModelListingSources.accepts("https://evil.com/model/example-model/sort/oldest/mpage/1"))
        assertFalse(ModelListingSources.accepts("http://www.elitebabes.com/model/example-model/sort/oldest/mpage/1"))
        assertFalse(ModelListingSources.accepts(first+"?token=secret"))
        assertFalse(ModelListingSources.accepts("https://www.elitebabes.com/model/example-model/sort/oldest/mpage/9999"))
        assertTrue(ModelListingSources.accepts("https://www.elitebabes.com/model/example-model/"))
    }


    @Test fun modelLandingPageAndExplicitNextLinksAreDiscovered() {
        val landing = "https://www.elitebabes.com/model/example-model/"
        val next = """<a rel="next" href="/model/example-model/sort/oldest/mpage/45/">Next</a>"""
        val result = ModelListingSources.parse(
            Jsoup.parse(html(card("https://www.elitebabes.com/example-gallery/", "Gallery", "2026-01-01"), next), landing),
            landing
        )!!
        assertEquals(1, result.photos.size)
        assertEquals(1, result.address.number)
        assertEquals(setOf(1, 45), result.pages.keys)
        assertEquals(100, ModelListingSources.MAX_PAGES)
    }

    @Test fun foreignNextLinkCannotEscapeModelIndex() {
        val foreign = """<a rel="next" href="https://foreign.example/model/example-model/sort/oldest/mpage/2/">Next</a>"""
        val result = ModelListingSources.parse(Jsoup.parse(html("", foreign), first), first)!!
        assertEquals(setOf(1), result.pages.keys)
    }

    @Test fun missingPageDoesNotLookLikeCompleteResult() {
        val p=ModelListingSources.parse(Jsoup.parse(html(""),first),first)!!
        val result=ModelListingSources.combine(listOf(p),listOf("Страница 2 недоступна"))
        assertTrue(result.incomplete)
        assertEquals(1,result.errors.size)
    }
    private fun apiPages(total: Int): List<ModelListingSources.Page> =
        (1..total).chunked(20).mapIndexed { index, ids ->
            val cards = ids.map { id ->
                ModelListingSources.Publication("https://www.elitebabes.com/gallery-$id/",
                    "Example gallery", "", "Example", sourceId = id.toString(),
                    type = if (id <= total - 41) "photo" else "video")
            }
            val videos = cards.filter { it.type == "video" }
            ModelListingSources.Page(ModelListingSources.Address("example", index + 1), "Example",
                cards.filter { it.type == "photo" }, videos.size, cards.size, emptyMap(),
                true, total, "https://www.elitebabes.com/gridapi/?content=model&nr=1&sort=oldest", videos)
        }

    @Test fun completeDeclaredListingRequiresContiguousUniqueBatches() {
        val pages = apiPages(432)
        assertTrue(ModelListingSources.coversDeclaredListing(pages))
        assertFalse(ModelListingSources.combine(pages).incomplete)
        assertFalse(ModelListingSources.coversDeclaredListing(pages.dropLast(1)))
        assertFalse(ModelListingSources.coversDeclaredListing(pages.reversed()))
        val last = pages.last()
        assertFalse(ModelListingSources.coversDeclaredListing(pages.dropLast(1) +
            last.copy(videoCards = List(12) { last.videoCards.first() })))
    }

    @Test fun exactMultipleOfPageSizeFinishesWithoutAnotherApiBatch() {
        val pages = apiPages(420)
        assertEquals(21, pages.size)
        assertEquals(20, pages.last().cardCount)
        assertTrue(ModelListingSources.coversDeclaredListing(pages))
        val result = ModelListingSources.combine(pages)
        assertFalse(result.incomplete)
        assertEquals(379, result.photos.size)
        assertEquals(41, result.videoCards.size)
        assertTrue(ModelListingSources.combine(pages.dropLast(1)).incomplete)
        assertTrue(ModelListingSources.combine(pages, errors = listOf("Сбой сети")).incomplete)

        val last = pages.last()
        val repeated = last.copy(videoCards = listOf(pages[19].videoCards.last()) + last.videoCards.drop(1))
        assertFalse(ModelListingSources.coversDeclaredListing(pages.dropLast(1) + repeated))
        val missing = pages.filterIndexed { index, _ -> index != 12 }
        assertFalse(ModelListingSources.coversDeclaredListing(missing + last))
    }
}
