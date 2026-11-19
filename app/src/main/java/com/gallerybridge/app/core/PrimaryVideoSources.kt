package com.gallerybridge.app.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Extract only the explicit media sources of the primary HTML5 player.
 * Excludes recommendation grids, advertisements, preview posters and links
 * to unrelated video pages. No guessing of CDN URLs or bypassing access rules.
 */
object PrimaryVideoSources {
    const val SOURCE = "Основной плеер"
    private val videoExtensions = setOf("mp4", "m4v", "mov", "webm")
    private val playerSelectors = listOf(
        "figure.stage.media-player video#thisPlayer",
        "figure.stage.media-player video",
        "video#thisPlayer",
        "main video",
        "video"
    )

    fun extract(doc: Document, pageUrl: String): List<Media> {
        if (XeroticaBridge.acceptsWatchPage(pageUrl) &&
            doc.selectFirst("video#thisPlayer, figure.stage.media-player video") == null)
            return emptyList()
        val player = playerSelectors.firstNotNullOfOrNull { doc.selectFirst(it) }
            ?: return emptyList()
        val origin = pageUrl.toHttpUrlOrNull() ?: return emptyList()
        if (origin.scheme != "https") return emptyList()
        val referer = origin.newBuilder().fragment(null).build().toString()
        val candidateElements = buildList {
            add(player)
            addAll(player.select("source[src]"))
        }
        val results = linkedMapOf<String, Media>()
        for (element in candidateElements) {
            val resolved = element.absUrl("src").toHttpUrlOrNull() ?: continue
            val extension = resolved.pathSegments.lastOrNull()
                ?.substringAfterLast('.', "")?.lowercase().orEmpty()
            if (resolved.scheme != "https" || extension !in videoExtensions) continue
            val url = resolved.toString()
            results.putIfAbsent(url, Media(
                url = url,
                name = Paths.clean(resolved.pathSegments.last()),
                kind = Kind.VIDEO,
                source = SOURCE,
                referrer = referer
            ))
        }
        return results.values.toList()
    }

    /** The poster must be explicit on the selected player, not a grid cover. */
    fun poster(doc: Document, pageUrl: String): String {
        val origin = pageUrl.toHttpUrlOrNull() ?: return ""
        if (origin.scheme != "https") return ""
        val player = playerSelectors.firstNotNullOfOrNull { doc.selectFirst(it) } ?: return ""
        val poster = player.absUrl("poster").toHttpUrlOrNull() ?: return ""
        if (poster.scheme != "https" || poster.port != 443) return ""
        val ext = poster.encodedPath.substringAfterLast('.', "").lowercase()
        return poster.toString().takeIf { ext in setOf("jpg", "jpeg", "png", "webp") }.orEmpty()
    }

    /** Nullable poster accessor retained for existing parser checks. */
    fun posterUrl(doc: Document, pageUrl: String): String? =
        poster(doc, pageUrl).takeIf { it.isNotBlank() }

    fun isVideoPage(doc: Document, pageUrl: String): Boolean {
        val path = pageUrl.toHttpUrlOrNull()?.encodedPath.orEmpty()
        return doc.selectFirst("figure.stage.media-player, video#thisPlayer") != null ||
            path.contains("/video/", ignoreCase = true)
    }
}
