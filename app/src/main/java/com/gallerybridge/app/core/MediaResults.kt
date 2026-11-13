package com.gallerybridge.app.core

/**
 * Render only a bounded slice of scan results on the Upload screen.
 * A page can contain hundreds of images from srcset and sidebar thumbnails.
 * Filtering and paging are pure and testable; Compose should memoize the result.
 */
object MediaResults {
    const val PAGE_SIZE = 30
    private val primarySources = setOf("Ссылка", "Видео", "Прямой URL", "Прямой файл")

    fun visible(
        all: List<Media>,
        showOthers: Boolean,
        recommendedUrls: Set<String>
    ): List<Media> =
        if (showOthers) all
        else all.filter { it.source in primarySources || it.url in recommendedUrls }

    fun page(all: List<Media>, limit: Int): List<Media> =
        all.take(limit.coerceIn(0, all.size))
}
