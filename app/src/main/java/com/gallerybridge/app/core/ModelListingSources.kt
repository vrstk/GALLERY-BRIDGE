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
    data class Publication(val url: String, val title: String, val date: String, val model: String)
    data class Page(
        val address: Address, val model: String, val photos: List<Publication>,
        val videos: Int, val cardCount: Int, val pages: Map<Int, String>,
        val infiniteLoader: Boolean, val advertisedCount: Int?
    )
    data class Result(
        val photos: List<Publication>, val videos: Int, val cards: Int,
        val loadedPages: Int, val advertisedCount: Int?, val incomplete: Boolean,
        val errors: List<String>
    )

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

    private fun publicationUrl(raw: String): String? {
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

    fun combine(
        pages: List<Page>, errors: List<String> = emptyList(), capped: Boolean = false
    ): Result {
        val photos = linkedMapOf<String, Publication>()
        pages.sortedBy { it.address.number }.forEach { page ->
            page.photos.forEach { photos.putIfAbsent(it.url, it) }
        }
        val cards = pages.sumOf { it.cardCount }
        val advertised = pages.mapNotNull { it.advertisedCount }.maxOrNull()
        val incomplete = capped || errors.isNotEmpty() ||
            pages.any { it.infiniteLoader || it.cardCount >= MAX_CARDS_PER_PAGE } ||
            (advertised != null && cards < advertised)
        return Result(
            photos.values.toList(), pages.sumOf { it.videos },
            cards, pages.size, advertised, incomplete, errors
        )
    }
}
