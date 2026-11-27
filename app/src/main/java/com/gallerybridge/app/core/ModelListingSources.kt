package com.gallerybridge.app.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import java.time.LocalDate

/**
 * Read-only model index, never a source of downloadable media thumbnails.
 * Numbered pages and the in-page infinite loader are separate mechanisms.
 */
object ModelListingSources {
    const val MAX_PAGES = 100
    const val MAX_CARDS_PER_PAGE = 750

    data class Address(val slug: String, val number: Int)
    data class Publication(
        val url: String, val title: String, val date: String, val model: String,
        val sourceId: String = "", val caption: String = "", val studio: String = "",
        val displayedDate: String = "", val type: String = "photo",
        val previewUrl: String = "", val watchUrl: String = ""
    )
    data class Page(
        val address: Address, val model: String, val photos: List<Publication>,
        val videos: Int, val cardCount: Int, val pages: Map<Int, String>,
        val infiniteLoader: Boolean, val advertisedCount: Int?,
        val apiUrl: String = "", val videoCards: List<Publication> = emptyList()
    )
    data class Result(
        val photos: List<Publication>, val videos: Int, val cards: Int,
        val loadedPages: Int, val advertisedCount: Int?, val incomplete: Boolean,
        val errors: List<String>, val videoCards: List<Publication> = emptyList()
    ) {
        val allCards: List<Publication> get() = photos + videoCards
    }

    private val route = Regex(
        """^/model/([a-z0-9-]+)(?:/sort/oldest(?:/mpage/([1-9][0-9]*))?)?/?$"""
    )
    private val count = Regex("""\(([0-9]{1,5})\s+Galleries\)""", RegexOption.IGNORE_CASE)

    fun address(raw: String): Address? {
        val u = raw.toHttpUrlOrNull() ?: return null
        if (u.scheme != "https" || u.port != 443 ||
            u.host !in setOf("elitebabes.com", "www.elitebabes.com") ||
            u.query != null || u.fragment != null) return null
        val m = route.matchEntire(u.encodedPath) ?: return null
        val page = m.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 1
        return if (page in 1..MAX_PAGES) Address(m.groupValues[1], page) else null
    }

    fun accepts(raw: String): Boolean = address(raw) != null

    internal fun publicationUrl(raw: String): String? {
        val u = raw.toHttpUrlOrNull() ?: return null
        if (u.scheme != "https" || u.port != 443 ||
            u.host !in setOf("elitebabes.com", "www.elitebabes.com") ||
            u.query != null || u.fragment != null ||
            u.encodedPath == "/" || u.encodedPath.startsWith("/model/") ||
            u.encodedPath.startsWith("/video/") || u.encodedPath.startsWith("/wp-")
        ) return null
        return u.toString()
    }

    fun parse(doc: Document, pageUrl: String): Page? {
        val source = address(pageUrl) ?: return null
        EliteBabesModelAdapter.parseFirst(doc, pageUrl)?.let { return it }
        val model = doc.selectFirst("main#content article.module-model h1")
            ?.text()?.trim()?.take(130).orEmpty()
        val gallery = doc.selectFirst("main#content > ul.list-gallery.has-mobile-menu")
        if (model.isBlank() || gallery == null) return null
        val pages = linkedMapOf(source.number to pageUrl)
        for (link in doc.select("#pagination_infinite a[href], a[rel=next][href], link[rel=next][href], .pagination a[href]")) {
            val href = link.absUrl("href")
            val page = address(href) ?: continue
            if (page.slug == source.slug) pages.putIfAbsent(page.number, href)
        }

        val cards = gallery.children().filter { it.tagName() == "li" }
            .take(MAX_CARDS_PER_PAGE)
        val photos = linkedMapOf<String, Publication>()
        var videos = 0
        for (card in cards) {
            val figure = card.selectFirst("figure") ?: continue
            if (figure.selectFirst("i.icon-play, video, a[data-av-bridged]") != null) {
                videos++
                continue
            }
            // Only publication anchors; never import img[src] thumbnail URLs.
            val overlay = figure.selectFirst("div.img-overlay p a[href]")
            val primary = figure.selectFirst("figure > a[href]")
            val url = publicationUrl(overlay?.absUrl("href").orEmpty())
                ?: publicationUrl(primary?.absUrl("href").orEmpty()) ?: continue
            val title = overlay?.text()?.trim()?.take(250)?.takeIf(String::isNotBlank)
                ?: primary?.attr("title")?.trim()?.take(250)?.takeIf(String::isNotBlank)
                ?: continue
            val rawDate = figure.selectFirst("span.img-label span")?.text()?.trim().orEmpty()
            val date = runCatching { LocalDate.parse(rawDate).toString() }.getOrDefault("")
            photos.putIfAbsent(url, Publication(url, title, date, model))
        }
        return Page(
            source, model, photos.values.toList(), videos, cards.size, pages,
            doc.select("#load-more, #loader").isNotEmpty(),
            count.find(doc.title())?.groupValues?.get(1)?.toIntOrNull()
        )
    }


    /**
     * Continue only after the server itself exposed numbered oldest-page links.
     * Never probe guessed hosts, arbitrary routes or out-of-range page numbers.
     * Stop at the cap; a blank/repeated page will be handled by the scanner.
     */
    fun nextNumberedPage(
        pages: List<Page>, attempted: Set<Int>
    ): Pair<Int, String>? {
        if (pages.isEmpty() || attempted.isEmpty()) return null
        val candidate = pages.asSequence().flatMap { it.pages.values.asSequence() }
            .firstOrNull { link ->
                val address = address(link)
                address != null && address.number > 1 &&
                    link.contains("/sort/oldest/mpage/")
            } ?: return null
        val expected = pages.first().address.slug
        val pattern = Regex("""/mpage/[1-9][0-9]*(?=/|$)""")
        val number = (attempted.maxOrNull() ?: return null) + 1
        if (number !in 2..MAX_PAGES) return null
        val next = candidate.replace(pattern, "/mpage/$number")
        return if (address(next) == Address(expected, number)) number to next else null
    }

    // Confirm coverage by the advertised count. The last batch may be full
    // when that count is a multiple of the page size. Require a contiguous
    // sequence of distinct publications, including on a resumed scan.
    fun coversDeclaredListing(pages: List<Page>): Boolean {
        val first = pages.firstOrNull() ?: return false
        val expected = first.advertisedCount ?: return false
        if (pages.size < 2 || first.apiUrl.isBlank() || expected <= 0 ||
            pages.sumOf { it.cardCount } != expected ||
            pages.last().cardCount !in 1..first.cardCount) return false
        val urls = mutableSetOf<String>()
        val ids = mutableSetOf<String>()
        return pages.withIndex().all { (index, page) ->
            page.address == Address(first.address.slug, index + 1) &&
                page.apiUrl == first.apiUrl && page.advertisedCount == expected &&
                (index == pages.lastIndex || page.cardCount == first.cardCount) &&
                page.cardCount == page.photos.size + page.videoCards.size &&
                (page.photos + page.videoCards).all { card ->
                    val url = GalleryKeys.canonical(card.url)
                    url != null && urls.add(url) &&
                        (card.sourceId.isBlank() || ids.add(card.sourceId))
                }
        }
    }

    fun combine(
        pages: List<Page>, errors: List<String> = emptyList(),
        capped: Boolean = false, apiConfirmedComplete: Boolean = false
    ): Result {
        val seen = mutableSetOf<String>()
        val photos = mutableListOf<Publication>()
        val videos = mutableListOf<Publication>()
        for (page in pages.sortedBy { it.address.number }) {
            for (card in page.photos + page.videoCards) {
                val key = if (card.sourceId.isNotBlank())
                    "elitebabes:" + card.sourceId
                else GalleryKeys.canonical(card.url) ?: card.url
                if (!seen.add(key)) continue
                if (card.type == "video") videos.add(card) else photos.add(card)
            }
        }
        val cards = pages.sumOf { it.cardCount }
        val advertised = pages.mapNotNull { it.advertisedCount }.maxOrNull()
        val incomplete = capped || errors.isNotEmpty() ||
            (pages.any { it.apiUrl.isNotBlank() } && !apiConfirmedComplete && !coversDeclaredListing(pages)) ||
            pages.any { it.cardCount >= MAX_CARDS_PER_PAGE ||
                (it.infiniteLoader && it.apiUrl.isBlank()) } ||
            (advertised != null && photos.size + videos.size < advertised)
        return Result(photos, pages.sumOf { it.videos }.coerceAtLeast(videos.size),
            cards, pages.size, advertised, incomplete, errors, videos)
    }
}
