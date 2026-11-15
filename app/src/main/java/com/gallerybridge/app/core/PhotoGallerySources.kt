package com.gallerybridge.app.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import java.util.Locale

/**
 * Selects the primary set of image files from a site's actual photo-gallery
 * lightbox, rather than collecting header thumbnails, ads and related galleries.
 * Returns null for unknown page layouts so generic discovery can still run.
 */
object PhotoGallerySources {
    fun extract(doc: Document, pageUrl: String): List<Media>? {
        val origin = pageUrl.toHttpUrlOrNull() ?: return null
        val host = origin.host.lowercase(Locale.ROOT)
        if (origin.scheme != "https" ||
            host != "elitebabes.com" && !host.endsWith(".elitebabes.com")
        ) return null

        val galleryGroups = doc.select("a.fancy[data-fancybox][href]")
            .filter { it.attr("data-fancybox").startsWith("gallery-", ignoreCase = true) }
            .groupBy { it.attr("data-fancybox") }

        val candidates = galleryGroups.values.maxByOrNull { group ->
            group.count { anchor -> isImageUrl(anchor.absUrl("href")) }
        } ?: return null

        val photos = linkedMapOf<String, Media>()
        for (anchor in candidates) {
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
