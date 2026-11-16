package com.gallerybridge.app.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

/**
 * Find explicit full-size gallery links rather than scraping surrounding thumbnails.
 * No URLs are invented. Unrecognized layouts still use generic discovery.
 */
object PhotoGallerySources {
    private val supportingNames = listOf(
        "related", "sidebar", "widget", "advert", "sponsor", "recommend", "promo"
    )
    private val contentNames = setOf(
        "post-content", "entry-content", "post-body", "article-body", "gallery-content"
    )

    private fun supportingArea(anchor: Element): Boolean = anchor.parents().any { parent ->
        val tag = parent.tagName().lowercase(Locale.ROOT)
        if (tag in setOf("aside", "nav", "footer") ||
            parent.attr("role").equals("complementary", ignoreCase = true)
        ) true else {
            val tokens = (parent.id() + " " + parent.className())
                .lowercase(Locale.ROOT).split(Regex("[\\s_\\-]+"))
            tokens.any { token -> supportingNames.any(token::startsWith) }
        }
    }

    private fun primaryArea(anchor: Element): Boolean = anchor.parents().any { parent ->
        parent.tagName().lowercase(Locale.ROOT) in setOf("main", "article") ||
            parent.classNames().any { it.lowercase(Locale.ROOT) in contentNames }
    }

    private fun galleryId(raw: String): Boolean {
        val id = raw.trim()
        return id.equals("gallery", ignoreCase = true) ||
            id.startsWith("gallery-", ignoreCase = true) ||
            id.startsWith("gallery_", ignoreCase = true)
    }

    fun extract(doc: Document, pageUrl: String): List<Media>? {
        val origin = pageUrl.toHttpUrlOrNull() ?: return null
        val host = origin.host.lowercase(Locale.ROOT)
        if (origin.scheme != "https" ||
            host != "elitebabes.com" && !host.endsWith(".elitebabes.com")
        ) return null

        // Some valid lightboxes omit the CSS class "fancy". Their HTML
        // data-fancybox association is the meaningful link to the gallery.
        val groups = doc.select("a[data-fancybox][href]")
            .filter { galleryId(it.attr("data-fancybox")) && !supportingArea(it) }
            .filter { isImageUrl(it.absUrl("href")) }
            .groupBy { it.attr("data-fancybox").trim().lowercase(Locale.ROOT) }

        // A smaller gallery in the article beats a larger related carousel.
        // Within the same location, choose the largest explicitly linked group.
        val anchors = groups.values.maxWithOrNull(
            compareBy<List<Element>>({ group -> group.count(::primaryArea) },
                { group -> group.size })
        ) ?: return null

        val photos = linkedMapOf<String, Media>()
        for (anchor in anchors) {
            val resolved = anchor.absUrl("href").toHttpUrlOrNull() ?: continue
            if (resolved.scheme != "https" || !isImageUrl(resolved.toString())) continue
            val filename = resolved.pathSegments.lastOrNull().orEmpty()
            val url = resolved.toString()
            photos.putIfAbsent(url, Media(url, Paths.clean(filename), Kind.PHOTO, "Ссылка"))
            if (photos.size >= 300) break
        }
        return photos.values.toList().takeIf { it.isNotEmpty() }
    }

    private fun isImageUrl(raw: String): Boolean {
        val url = raw.toHttpUrlOrNull() ?: return false
        val name = url.pathSegments.lastOrNull().orEmpty()
        return url.scheme == "https" &&
            name.substringAfterLast('.', "").lowercase(Locale.ROOT) in
            setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")
    }
}
