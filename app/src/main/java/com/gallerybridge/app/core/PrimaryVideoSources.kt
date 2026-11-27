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

    private fun elitePage(pageUrl: String): Boolean =
        pageUrl.toHttpUrlOrNull()?.host in setOf("elitebabes.com", "www.elitebabes.com")

    fun supportsTransfer(media: Media): Boolean {
        if (media.kind != Kind.VIDEO || media.source != SOURCE) return false
        val page = media.referrer.toHttpUrlOrNull() ?: return false
        val file = media.url.toHttpUrlOrNull() ?: return false
        if (listOf(page, file).any { it.scheme != "https" || it.port != 443 ||
                it.username.isNotEmpty() || it.password.isNotEmpty() }) return false
        if (XeroticaBridge.acceptsWatchPage(media.referrer)) return true
        return elitePage(media.referrer) && file.host == "media.elitebabes.com" &&
            file.encodedPath.endsWith(".mp4", ignoreCase = true)
    }

    private fun player(doc: Document, pageUrl: String): Element? {
        if (!elitePage(pageUrl)) return playerSelectors.firstNotNullOfOrNull { doc.selectFirst(it) }
        val candidates = doc.select("video").filter { video ->
            video.closest("aside, nav, footer, .list-gallery, .related, .recommendations, .advertisement") == null
        }
        val marked = candidates.filter { it.id() == "thisPlayer" || it.closest("figure.stage.media-player") != null }
        if (marked.isNotEmpty()) return marked.singleOrNull()
        // A publication can use a plain video element with only nested sources.
        return candidates.filter { video ->
            (listOf(video) + video.select("source[src]")).any { element ->
                val src = element.absUrl("src").toHttpUrlOrNull()
                src != null && src.scheme == "https" && src.host == "media.elitebabes.com" &&
                    src.encodedPath.endsWith(".mp4", ignoreCase = true)
            }
        }.singleOrNull()
    }

    fun extract(doc: Document, pageUrl: String): List<Media> {
        if (XeroticaBridge.acceptsWatchPage(pageUrl) &&
            doc.selectFirst("video#thisPlayer, figure.stage.media-player video") == null)
            return emptyList()
        val player = player(doc, pageUrl) ?: return emptyList()
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
        val player = player(doc, pageUrl) ?: return ""
        val poster = player.absUrl("poster").toHttpUrlOrNull() ?: return ""
        if (poster.scheme != "https" || poster.port != 443) return ""
        val ext = poster.encodedPath.substringAfterLast('.', "").lowercase()
        return poster.toString().takeIf { ext in setOf("jpg", "jpeg", "png", "webp") }.orEmpty()
    }

    /** Nullable poster accessor retained for existing parser checks. */
    fun posterUrl(doc: Document, pageUrl: String): String? =
        poster(doc, pageUrl).takeIf { it.isNotBlank() }

    fun isVideoPage(doc: Document, pageUrl: String): Boolean {
        if (elitePage(pageUrl)) return player(doc, pageUrl) != null
        val path = pageUrl.toHttpUrlOrNull()?.encodedPath.orEmpty()
        return doc.selectFirst("figure.stage.media-player, video#thisPlayer") != null ||
            path.contains("/video/", ignoreCase = true)
    }
}
