package com.gallerybridge.app.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

/**
 * Read explicitly linked gallery originals from the publication page only.
 * Neither preview <img> sources nor invented CDN file names are downloadable.
 */
object PhotoGallerySources {
    private val supportingNames = listOf(
        "related", "sidebar", "widget", "advert", "sponsor", "recommend", "promo"
    )
    private val contentNames = setOf(
        "post-content", "entry-content", "post-body", "article-body", "gallery-content"
    )
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")
    private val thumbnailSuffix = Regex(
        """(?i)[_-](?:masonry[_-]?[0-9]{2,4}|w(?:[0-8][0-9]{2})|thumb(?:nail)?|preview|small|cover|poster)$"""
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

    private fun galleryGroup(anchor: Element): String? {
        val modern = anchor.attr("data-fancybox").trim().lowercase(Locale.ROOT)
        if (galleryId(modern)) return modern
        // Fancybox also documents image groups and older data-fancybox-group.
        // Accept these only inside the publication, never from a page-wide ad.
        if (modern in setOf("images", "photos") && primaryArea(anchor)) return modern
        val legacy = anchor.attr("data-fancybox-group").trim().lowercase(Locale.ROOT)
        if (galleryId(legacy) && primaryArea(anchor)) return "legacy:$legacy"
        // Legacy Fancybox sometimes marks article anchors only by class=fancy.
        // Do not treat arbitrary image links or standalone cover thumbnails as a gallery.
        if (anchor.hasClass("fancy") && primaryArea(anchor)) return "article:fancy"
        return null
    }

    private fun originalUrl(anchor: Element): String? {
        // Both href and data-src are first-party, explicit Fancybox source
        // attributes. img[src], srcset and background-image are NOT evidence.
        for (attribute in listOf("href", "data-src")) {
            val url = anchor.absUrl(attribute).toHttpUrlOrNull() ?: continue
            val filename = url.pathSegments.lastOrNull().orEmpty()
            val suffix = filename.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val stem = filename.substringBeforeLast('.', "")
            if (url.scheme != "https" || url.port != 443 ||
                (url.host != "elitebabes.com" && !url.host.endsWith(".elitebabes.com")) ||
                suffix !in imageExtensions || thumbnailSuffix.containsMatchIn(stem) ||
                url.pathSegments.any {
                    it.lowercase(Locale.ROOT) in
                        setOf("thumb", "thumbs", "thumbnail", "thumbnails", "previews", "posters")
                }
            ) continue
            return url.toString()
        }
        return null
    }

    private fun linkedPhotoGroup(anchor: Element, photo: String): String? {
        val full = photo.toHttpUrlOrNull() ?: return null
        val image = anchor.selectFirst("img") ?: return null
        val base = full.encodedPath.substringBeforeLast('.')
        val stem = base.replace(Regex("""_[1-9][0-9]{2,4}$"""), "")
        for (attribute in listOf("data-src", "src")) {
            val thumb = image.absUrl(attribute).toHttpUrlOrNull() ?: continue
            val small = thumb.encodedPath.substringBeforeLast('.')
            if (thumb.host == full.host && thumbnailSuffix.containsMatchIn(small) &&
                small.replace(thumbnailSuffix, "") == stem) {
                return "linked:" + stem.substringBeforeLast('/')
            }
        }
        return null
    }

    fun extract(doc: Document, pageUrl: String): List<Media>? {
        val origin = pageUrl.toHttpUrlOrNull() ?: return null
        val host = origin.host.lowercase(Locale.ROOT)
        if (origin.scheme != "https" ||
            host != "elitebabes.com" && !host.endsWith(".elitebabes.com")
        ) return null

        val groups = doc.select("a[data-fancybox], a[data-fancybox-group], a.fancy, " +
            "article .gallery a[href], article .wp-block-gallery a[href], article a[rel~=^lightbox], main#content a[href]:has(img), article a[href]:has(img)")
            .filterNot(::supportingArea)
            .mapNotNull { anchor ->
                val photo = originalUrl(anchor) ?: return@mapNotNull null
                val group = galleryGroup(anchor) ?: run {
                    if (anchor.selectFirst("img") == null) return@mapNotNull null
                    val container = anchor.closest(".gallery, .wp-block-gallery")
                    if (container != null) "container:" + container.cssSelector()
                    else if (anchor.attr("rel").matches(Regex("""lightbox(?:\[[\w-]+\])?""")))
                        "rel:" + anchor.attr("rel")
                    else linkedPhotoGroup(anchor, photo) ?: return@mapNotNull null
                }
                Triple(group, anchor, photo)
            }.groupBy { it.first }

        // Never concatenate different groups, related sets and adverts.
        val chosen = groups.values.maxWithOrNull(
            compareBy<List<Triple<String, Element, String>>>(
                { group -> group.count { primaryArea(it.second) } },
                { group -> group.size }
            )
        ) ?: return null
        val photos = linkedMapOf<String, Media>()
        for ((_, _, url) in chosen) {
            val filename = url.toHttpUrlOrNull()?.pathSegments?.lastOrNull().orEmpty()
            photos.putIfAbsent(url, Media(url, Paths.clean(filename), Kind.PHOTO, "Ссылка"))
            if (photos.size >= 300) break
        }
        return photos.values.toList().takeIf { it.isNotEmpty() }
    }
}
