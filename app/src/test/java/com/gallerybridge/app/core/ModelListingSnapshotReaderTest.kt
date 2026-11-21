package com.gallerybridge.app.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class ModelListingSnapshotReaderTest {
    private val page = "https://www.elitebabes.com/model/example-model/sort/oldest/mpage/2/"
    private val first = "https://www.elitebabes.com/model/example-model/sort/oldest/mpage/1/"
    private val boundary = "----test-boundary--2026"

    private fun html(): String = """
        <!DOCTYPE html><html><head><title>Example Model (432 Galleries)</title></head>
        <body><main id="content">
          <article class="module-model"><h1>Example Model</h1></article>
          <ul class="list-gallery has-mobile-menu">
          <li><figure><span class="img-label"><span>2025-01-01</span></span>
            <a href="/photo-1/"><img src="https://cdn.elitebabes.com/masonry_400.jpg"/></a>
            <div class="img-overlay"><p><a href="/photo-1/">Снимок №1</a></p></div>
          </figure></li>
          <li><figure><span class="img-label"><i class="icon-play"></i></span>
            <a href="/video-1/"><video src="https://cdn.elitebabes.com/v.mp4"></video></a>
            <div class="img-overlay"><p><a href="/video-1/">Video</a></p></div>
          </figure></li>
          </ul><div id="pagination_infinite"><a href="$first">1</a></div>
          <div id="loader"></div>
        </main></body></html>
    """.trimIndent()

    private fun mht(
        origin: String = page,
        location: String = page,
        body: String = html(),
        encoding: String = "quoted-printable",
        ending: Boolean = true
    ): ByteArray {
        val payload = if (encoding == "quoted-printable") {
            body.toByteArray(StandardCharsets.UTF_8)
                .joinToString("") { "=%02X".format(it.toInt() and 255) }
                .chunked(75).joinToString("=\r\n")
        } else body
        return buildString {
            append("From: <Saved by Blink>\r\n")
            append("Snapshot-Content-Location: $origin\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: multipart/related;\r\n")
            append(" boundary=\"$boundary\"\r\n\r\n")
            append("--$boundary\r\nContent-Type: text/html\r\n")
            append("Content-Transfer-Encoding: $encoding\r\n")
            append("Content-Location: $location\r\n\r\n")
            append(payload)
            append("\r\n")
            if (ending) {
                append("--$boundary\r\nContent-Type: image/jpeg\r\n\r\n")
                append("SIMULATED-IMAGE-CONTENT-NOT-HTML")
                append("\r\n--$boundary--\r\n")
            }
        }.toByteArray(StandardCharsets.ISO_8859_1)
    }

    @Test fun readsChromeQuotedPrintableAndExcludesResourceParts() {
        val source = ModelListingSnapshotReader.read(ByteArrayInputStream(mht()))
        assertEquals(page, source.url)
        assertTrue(source.html.contains("Снимок №1"))
        assertFalse(source.html.contains("SIMULATED-IMAGE-CONTENT"))
        val cards = ModelListingSnapshotReader.parse(ByteArrayInputStream(mht()))
        assertEquals(2, cards.cardCount)
        assertEquals(1, cards.videos)
        assertEquals(1, cards.photos.size)
        assertEquals("Снимок №1", cards.photos.single().title)
        assertEquals("2025-01-01", cards.photos.single().date)
        assertEquals("https://www.elitebabes.com/photo-1/", cards.photos.single().url)
    }

    @Test fun rejectsOtherModelsAndUntrustedOrigins() {
        val wrong = "https://www.elitebabes.com/model/another-model/sort/oldest/mpage/2/"
        assertThrows(IllegalArgumentException::class.java) {
            ModelListingSnapshotReader.read(ByteArrayInputStream(mht(origin = wrong)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelListingSnapshotReader.read(ByteArrayInputStream(mht(location = "https://other.example/x")))
        }
    }

    @Test fun damagedOrUnsupportedMimeCannotSilentlyProducePartialListing() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelListingSnapshotReader.read(ByteArrayInputStream(mht(ending = false)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelListingSnapshotReader.read(ByteArrayInputStream(mht(encoding = "base64")))
        }
        val broken = mht().toString(StandardCharsets.ISO_8859_1)
            .replace("=3C=21=44", "=3C=XX=44")
        assertThrows(IllegalArgumentException::class.java) {
            ModelListingSnapshotReader.read(ByteArrayInputStream(broken.toByteArray(StandardCharsets.ISO_8859_1)))
        }
    }

    @Test fun plainHtmlOrMalformedBoundaryCannotBeMistakenForMht() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelListingSnapshotReader.read(ByteArrayInputStream(html().toByteArray()))
        }
        val invalid = mht().toString(StandardCharsets.ISO_8859_1).replace(
            " boundary=\"$boundary\"", " boundary=\"invalid\""
        )
        assertThrows(IllegalArgumentException::class.java) {
            ModelListingSnapshotReader.read(ByteArrayInputStream(invalid.toByteArray(StandardCharsets.ISO_8859_1)))
        }
    }

    @Test fun alreadySavedLongHtmlIsParsedBeyondTwentyCards() {
        val manyCards = (1..130).joinToString("") { id ->
            """<li><figure><a href="/gallery-$id/"></a><div class="img-overlay">
              <p><a href="/gallery-$id/">Gallery $id</a></p></div></figure></li>"""
        }
        val big = html().replace("<ul class=\"list-gallery has-mobile-menu\">",
            "<ul class=\"list-gallery has-mobile-menu\">$manyCards")
        val cards = ModelListingSnapshotReader.parse(ByteArrayInputStream(mht(body = big)))
        assertEquals(132, cards.cardCount)
        assertEquals(131, cards.photos.size)
    }
}
