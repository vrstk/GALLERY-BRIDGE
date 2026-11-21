package com.gallerybridge.app.core

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document

/**
 * Follow only an explicit link from the main Elite Babes publication to a
 * XErotica watch page. Never infer the external page from a model or post ID.
 */
internal object XeroticaBridge {
    fun acceptsWatchPage(raw: String): Boolean {
        val url = raw.toHttpUrlOrNull() ?: return false
        return url.scheme == "https" && url.port == 443 &&
            url.username.isEmpty() && url.password.isEmpty() &&
            url.host in setOf("xerotica.com", "www.xerotica.com") &&
            url.encodedPath.startsWith("/video/") && url.encodedPath.endsWith(".html")
    }

    fun watchTarget(raw: String): String? {
        if (acceptsWatchPage(raw)) return raw
        val bridge = raw.toHttpUrlOrNull() ?: return null
        if (bridge.scheme != "https" || bridge.port != 443 ||
            bridge.host !in setOf("xerotica.com", "www.xerotica.com") ||
            bridge.username.isNotEmpty() || bridge.password.isNotEmpty() ||
            bridge.encodedPath != "/av_bridge.php") return null
        val path = bridge.queryParameterValues("to").singleOrNull() ?: return null
        if (!path.startsWith("/video/")) return null
        val target = bridge.resolve(path) ?: return null
        return target.toString().takeIf { target.host == bridge.host && acceptsWatchPage(it) }
    }

    fun externalPage(doc: Document, sourceUrl: String): String? {
        val source = sourceUrl.toHttpUrlOrNull() ?: return null
        if (source.scheme != "https" || source.port != 443 ||
            source.host !in setOf("elitebabes.com", "www.elitebabes.com")) return null

        val found = doc.select(
            "main article a[href], article .post-content a[href], " +
            "main .post-content a[href], main .entry-content a[href], " +
            "article .entry-content a[href]"
        ).mapNotNull { link ->
            if (link.parents().any { it.tagName() in setOf("aside", "nav", "footer") ||
                    it.classNames().any { cls ->
                        cls.contains("related", true) || cls.contains("recommend", true)
                    } }) return@mapNotNull null
            watchTarget(link.absUrl("href"))
        }.distinct()
        // Multiple unrelated outbound watch pages are not enough to prove identity.
        return found.singleOrNull()
    }
}
