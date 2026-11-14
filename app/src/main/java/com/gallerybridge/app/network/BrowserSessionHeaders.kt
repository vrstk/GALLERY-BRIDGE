package com.gallerybridge.app.network

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebSettings
import com.gallerybridge.app.core.Media
import okhttp3.Request

/**
 * Browser-associated downloads use cookies scoped by Android WebView to the
 * *destination* URL. Never persist cookies in the media queue or log them.
 * This can help with authorized session-bound media; it is not a DRM bypass.
 */
object BrowserSessionHeaders {
    const val SOURCE = "Встроенный браузер"

    fun isBrowserMedia(media: Media): Boolean =
        media.source == SOURCE && media.referrer.startsWith("https://") &&
            media.url.startsWith("https://")

    fun withContext(builder: Request.Builder, media: Media, context: Context): Request.Builder {
        if (!isBrowserMedia(media)) return builder
        val origin = Uri.parse(media.referrer)
        if (origin.scheme != "https" || origin.host.isNullOrBlank()) return builder
        // Avoid leaking page query parameters, which can contain sensitive tokens.
        val reference = media.referrer.substringBefore('#').substringBefore('?')
        builder.header("Referer", reference)
        builder.header("User-Agent", WebSettings.getDefaultUserAgent(context))
        val cookies = try { CookieManager.getInstance().getCookie(media.url) } catch (_: Exception) { null }
        if (!cookies.isNullOrBlank()) builder.header("Cookie", cookies)
        return builder
    }
}
