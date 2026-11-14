package com.gallerybridge.app.core

/**
 * Compares only explicitly discovered variants in the same CDN directory
 * and with the same normalized image stem. No URLs are constructed.
 * Unrelated thumbnails are never assumed to represent the same photo.
 */
object VariantComparison {
    const val MAX_PAIRS = 20

    data class Pairing(val chosen: Media, val alternative: Media)
    data class Dimensions(val width: Int, val height: Int) {
        val pixels: Long get() = width.toLong() * height.toLong()
    }
    data class Improvement(val chosen: Media, val alternative: Media, val old: Dimensions, val newer: Dimensions)

    fun pairs(all: List<Media>, selectedUrls: Set<String>): List<Pairing> {
        val selectedPhotos = all.filter { it.kind == Kind.PHOTO && it.url in selectedUrls }
            .distinctBy(QualitySelector::groupKey)
        val groups = all.filter { it.kind == Kind.PHOTO }.groupBy(QualitySelector::groupKey)
        return selectedPhotos.mapNotNull { chosen ->
            val best = groups[QualitySelector.groupKey(chosen)].orEmpty()
                .filter { it.url != chosen.url && it.url !in selectedUrls }
                .maxWithOrNull(compareBy<Media>({ QualitySelector.rank(it) }, { it.source == "Ссылка" }))
            if (best == null) null else Pairing(chosen, best)
        }.take(MAX_PAIRS)
    }

    fun candidates(pairs: List<Pairing>): List<Media> =
        pairs.flatMap { listOf(it.chosen, it.alternative) }.distinctBy(Media::url)

    fun improvements(pairs: List<Pairing>, measured: Map<String, Dimensions>): List<Improvement> =
        pairs.mapNotNull { pair ->
            val old = measured[pair.chosen.url] ?: return@mapNotNull null
            val newer = measured[pair.alternative.url] ?: return@mapNotNull null
            if (newer.pixels > old.pixels)
                Improvement(pair.chosen, pair.alternative, old, newer)
            else null
        }

    fun comparableCount(pairs: List<Pairing>, measured: Map<String, Dimensions>): Int =
        pairs.count { measured[it.chosen.url] != null && measured[it.alternative.url] != null }
}
