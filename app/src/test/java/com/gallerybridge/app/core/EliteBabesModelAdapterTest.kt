package com.gallerybridge.app.core

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class EliteBabesModelAdapterTest {
    private val profile = "https://www.elitebabes.com/model/alisa-i/sort/oldest/"
    private val api = "https://www.elitebabes.com/gridapi/?content=model&nr=67303&sort=oldest"

    @Test fun apiMustComeFromPageAndBeBounded() {
        assertEquals(api + "&mpage=2", EliteBabesModelAdapter.nextUrl(api, 2))
        assertNull(EliteBabesModelAdapter.validatedApiUrl(api.replace("67303", "bad")))
        assertNull(EliteBabesModelAdapter.validatedApiUrl("http://www.elitebabes.com/gridapi/?content=model&nr=67303&sort=oldest"))
        assertNull(EliteBabesModelAdapter.validatedApiUrl(api.replace("content=model", "content=post")))
        assertNull(EliteBabesModelAdapter.validatedApiUrl(api + "&mpage=2"))
        assertEquals("",
            EliteBabesModelAdapter.splitCaption("at Teen Dreams").first)
        val html = """<html><head><title>Alisa (99 Galleries)</title></head>
            <body><main id="content"><article class="module-model"><h1>Alisa</h1></article>
            <ul class="list-gallery"><li data-postid="67320"><figure>
            <div class="img-overlay"><p><a href="/photo-one/">First at Femjoy</a></p></div>
            <span class="img-label"><span>2017-09-05</span></span></figure></li></ul></main>
            <script>var apiUrl = "$api";</script></body></html>"""
        val doc = Jsoup.parse(html, profile)
        val page = requireNotNull(EliteBabesModelAdapter.parseFirst(doc, profile))
        assertEquals(api, page.apiUrl)
        assertEquals("67320", page.photos.single().sourceId)
        assertEquals("First", page.photos.single().title)
        assertEquals("Femjoy", page.photos.single().studio)
    }

    @Test fun captionLinkOverridesForeignVideoCover() {
        val fragment = """<ul class="list-gallery"><li class="video" data-postid="150319">
            <figure><a href="https://another.example/video"></a>
            <i class="icon-play"></i>
            <div class="img-overlay"><p>
            <a href="https://www.elitebabes.com/accepted-video/">Bouncing at Femjoy</a>
            </p></div><span class="img-label"><span>2017-12-30</span></span></figure></li>
            <li data-postid="106421"><figure><div class="img-overlay"><p>
            <a href="/missing-title/">at Teen Dreams</a></p></div></figure></li></ul>"""
        val cards = EliteBabesModelAdapter.parseCards(Jsoup.parse(fragment, profile), "Alisa")
        assertEquals(2, cards.size)
        assertEquals("video", cards[0].type)
        assertEquals("https://www.elitebabes.com/accepted-video/", cards[0].url)
        assertEquals("", cards[1].title)
        assertEquals("Teen Dreams", cards[1].studio)
    }

    @Test fun apiPlaceholderAndRepeatDoNotMeanEnd() {
        assertTrue(EliteBabesModelAdapter.isUnavailable("<html>Site Unavailable</html>"))
        assertThrows(IllegalArgumentException::class.java) {
            EliteBabesModelAdapter.parseApiResponse("Site Unavailable", api, "Alisa")
        }
        assertTrue(EliteBabesModelAdapter.parseApiResponse("[]", api, "Alisa").end)
        assertFalse(EliteBabesModelAdapter.parseApiResponse(
            """<ul class="list-gallery"><li data-postid="12"><figure>
            <div class="img-overlay"><p><a href="/photo/">A at Metart</a></p></div>
            </figure></li></ul>""", api, "Alisa").end)
        assertThrows(IllegalStateException::class.java) {
            EliteBabesModelAdapter.parseApiResponse("<html>No galleries</html>", api, "Alisa")
        }
    }

    @Test fun gridFragmentsUseTheIdFromTheCardControl() {
        val fragment = """<li><figure><div class="img-overlay"><p>
            <a href="/publication/">First at Femjoy</a></p><ul><li>
            <a class="simplefavorite-button" data-postid="67320"></a>
            </li></ul></div></figure></li>"""
        val items = JSONArray().put(JSONObject().put("html", fragment))
        val batch = EliteBabesModelAdapter.parseApiResponse(items.toString(), api, "Alisa")
        assertEquals("67320", batch.cards.single().sourceId)
        assertFalse(batch.end)
        assertTrue(EliteBabesModelAdapter.parseApiResponse("[ ]", api, "Alisa").end)
        items.put(JSONObject().put("unexpected", 42))
        assertThrows(IllegalArgumentException::class.java) {
            EliteBabesModelAdapter.parseApiResponse(JSONObject().put("items", items).toString(), api, "Alisa")
        }
    }

    @Test fun diagnosticNinetyPhotosNineVideosAndIdempotentCatalogPlan() {
        val galleries = JSONArray()
        repeat(99) { index ->
            val video = index % 11 == 0
            val id = if (index == 48) 106421 else 200000 + index
            galleries.put(JSONObject()
                .put("sourceId", id.toString())
                .put("url", "https://www.elitebabes.com/gallery-$id/")
                .put("caption", if (id == 106421) "at Teen Dreams" else "Same Title at Femjoy")
                .put("displayedDate", "2018-11-15")
                .put("type", if (video) "video" else "photo")
                .put("previewUrl", "https://cdn.elitebabes.com/content/181125/${id}_masonry_400.jpg"))
        }
        val diagnostic = JSONObject()
            .put("report", "GB EliteBabes diagnostics")
            .put("pageUrl", profile)
            .put("profile", JSONObject().put("name", "Alisa"))
            .put("inlineApiUrls", JSONArray().put(api))
            .put("galleries", galleries)
            .put("loadedCards", 99).put("photoCards", 90).put("videoCards", 9)
            .put("titleDeclaredCount", 99)
        val parsed = EliteBabesModelAdapter.parseDiagnostic(diagnostic.toString())
        assertEquals(99, parsed.cards.size)
        assertEquals(90, parsed.cards.count { it.type == "photo" })
        assertEquals(9, parsed.cards.count { it.type == "video" })
        val first = GalleryCatalogBackup.plan(emptyList(), parsed.catalogEntries(1L))
        assertEquals(99, first.added)
        val second = GalleryCatalogBackup.plan(first.records, parsed.catalogEntries(2L))
        assertEquals(0, second.added)
        assertEquals(99, second.alreadyPresent)
        assertEquals("", parsed.cards.single { it.sourceId == "106421" }.title)
    }
}
