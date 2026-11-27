package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test
import org.jsoup.Jsoup

class ModelListingCheckpointStoreTest {
    private val root = "https://www.elitebabes.com/model/example-model/"
    private val page2 = "https://www.elitebabes.com/model/example-model/sort/oldest/mpage/2/"
    private val card = """
        <html><head><title>Example Model (40 Galleries)</title></head><body>
          <main id="content"><article class="module-model"><h1>Example Model</h1></article>
          <ul class="list-gallery has-mobile-menu"><li><figure>
            <a href="https://www.elitebabes.com/gallery-one/"><img src="thumb.jpg"></a>
            <div class="img-overlay"><p><a href="https://www.elitebabes.com/gallery-one/">Title</a></p></div>
          </figure></li></ul>
          <div id="pagination_infinite"><a href="$page2">2</a></div>
        </main></body></html>
    """

    @Test fun checkpointRoundTripKeepsOnlyValidatedPages() {
        val page = requireNotNull(ModelListingSources.parse(Jsoup.parse(card, root), root))
        val checkpoint = ModelListingCheckpointStore.Checkpoint(
            "123e4567-e89b-12d3-a456-426614174000", root, listOf(page))
        val loaded = requireNotNull(ModelListingCheckpointStore.decode(
            ModelListingCheckpointStore.encode(checkpoint)))
        assertFalse(loaded.finished)
        assertEquals(1, loaded.pages.size)
        assertEquals(1, loaded.result().photos.size)
        assertTrue(loaded.result().incomplete)
        assertEquals(page2, loaded.pages[0].pages[2])
    }

    @Test fun malformedAndForeignCheckpointIsDiscarded() {
        assertNull(ModelListingCheckpointStore.decode("broken"))
        assertNull(ModelListingCheckpointStore.decode("""{"format":1,"id":"12345678","source":"http://evil.test","pages":[]}"""))
    }

    @Test fun resumesSequentialPageOnlyAfterServerAdvertisedNumberedPagination() {
        val parsed = requireNotNull(ModelListingSources.parse(Jsoup.parse(card, root), root))
        assertEquals(3 to "https://www.elitebabes.com/model/example-model/sort/oldest/mpage/3/",
            ModelListingSources.nextNumberedPage(listOf(parsed), setOf(1,2)))
        assertNull(ModelListingSources.nextNumberedPage(listOf(parsed), setOf(100)))
        val noPagination = parsed.copy(pages = mapOf(1 to root))
        assertNull(ModelListingSources.nextNumberedPage(listOf(noPagination), setOf(1)))
    }
}
