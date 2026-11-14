package com.gallerybridge.app.core

import java.net.URI

/**
 * Finds the best explicitly linked media variant on a page.
 * Filename suffixes and HTML srcset descriptors are hints only, not verified
 * image dimensions and never proof of the site's highest-resolution original.
 */
object QualitySelector {
    private val suffix = Regex(
        "(?i)_(?:w)?(400|640|800|960|1024|1200|1280|1440|1600|1800|1920|2000|2400|2560|3000|3200|3840|4000)$"
    )
    private val descriptor = Regex("(?i)\\b(\\d{3,4})w\\b")
    private val videoSuffix = Regex("(?i)_(180|240|360|480|540|720|1080|1440|2160|4320)$")
    private fun videoHeight(media: Media): Int? =
        if (media.kind == Kind.VIDEO) videoSuffix.find(media.name.substringBeforeLast('.', media.name))
            ?.groupValues?.get(1)?.toIntOrNull() else null

    private fun fileStem(media: Media): String {
        val name = media.name.substringBeforeLast('.', media.name)
        return name.replace(if (media.kind == Kind.VIDEO) videoSuffix else suffix, "")
    }

    fun groupKey(media: Media): String {
        val u = runCatching { URI(media.url) }.getOrNull()
        val folder = u?.rawPath?.substringBeforeLast('/', "").orEmpty()
        val host = u?.host.orEmpty().lowercase()
        return listOf(media.kind.name, host, folder, fileStem(media).lowercase()).joinToString("|")
    }

    private fun suggestedWidth(media: Media): Int? {
        val name = media.name.substringBeforeLast('.', media.name)
        val fromName = suffix.find(name)?.groupValues?.get(1)?.toIntOrNull()
        val fromSrcset = descriptor.find(media.source)?.groupValues?.get(1)?.toIntOrNull()
        return listOfNotNull(fromName, fromSrcset).maxOrNull()
    }

    /** Score only published URL / HTML hints; no invented URLs. */
    fun rank(media: Media): Int {
        videoHeight(media)?.let { return 5000 + it }
        if (media.kind == Kind.VIDEO) return 4100
        val width = suggestedWidth(media)
        if (width != null) return if (width <= 640) 1000 + width else 3000 + width
        return when {
            media.source.contains("original", ignoreCase = true) -> 4300
            media.source in setOf("Ссылка", "Прямой URL", "Прямой файл", "Видео") -> 4100
            media.source.contains("srcset", ignoreCase = true) -> 3000
            else -> 2000
        }
    }

    fun hint(media: Media): String {
        if (media.kind == Kind.VIDEO) return videoHeight(media)?.let {
            "Вариант ${it}p (по имени, качество не проверено)"
        } ?: "Разрешение видео неизвестно"
        val width = suggestedWidth(media)
        return if (width != null) {
            "Ориентир: " + width + " px (по ссылке/HTML, без проверки)"
        } else {
            "Разрешение не определено"
        }
    }

    /**
     * When the page has direct media links, anchor the choice to their groups.
     * This avoids auto-selecting unrelated sidebar thumbnails and advertising.
     * If there are no direct links, use explicit original/srcset media candidates.
     */
    fun recommended(media: List<Media>): Set<String> {
        val direct = media.filter {
            it.source in setOf("Ссылка", "Прямой URL", "Прямой файл", "Видео")
        }
        val candidates = if (direct.isNotEmpty()) {
            val groups = direct.map(::groupKey).toSet()
            media.filter { it in direct || groupKey(it) in groups }
        } else {
            media.filter {
                it.source.contains("original", ignoreCase = true) ||
                    it.source.contains("srcset", ignoreCase = true) ||
                    it.source == "Изображение (качество не подтверждено)"
            }
        }
        return candidates.groupBy(::groupKey).values.mapNotNull { variants ->
            variants.maxWithOrNull(
                compareBy<Media>({ rank(it) }, { it.source == "Ссылка" })
            )?.url
        }.toSet()
    }
}
