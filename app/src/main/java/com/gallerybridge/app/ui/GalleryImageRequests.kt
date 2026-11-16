package com.gallerybridge.app.ui

import android.content.Context
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/**
 * Shared cache policy for gallery previews and user-opened full-size photos.
 * Coil's singleton ImageLoader owns the cache; neither the selection nor the
 * upload queue depends on cache entries. Android can evict them at any time.
 */
internal object GalleryImageRequests {
    fun forUrl(context: Context, url: String): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .apply {
                if (url.startsWith("https://")) {
                    httpHeaders(NetworkHeaders.Builder()
                        .set("User-Agent", "GalleryBridge/0.1").build())
                }
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build()
}
