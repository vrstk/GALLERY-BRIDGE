package com.gallerybridge.app.core

import com.gallerybridge.app.transfer.QueueStore

/**
 * A non-persistent, read-only list of saved gallery page URLs.
 * It NEVER schedules downloads, creates queue entries or starts network work.
 * Previously processed and currently queued galleries require individual review
 * and cannot be mass-selected for a new transfer.
 */
internal object GalleryBatchSelection {
    const val LIMIT = 25

    fun eligibleUrls(
        entries: List<CatalogGallery>,
        queue: GalleryCatalogProgress.QueueIndex
    ): Set<String> {
        val singleGallery = queue.snapshot?.let { GalleryKeys.canonical(it.sourceUrl) }
        return entries.asSequence()
            .filter { it.target.isBlank() }
            .mapNotNull { GalleryKeys.canonical(it.url) }
            .filter { it != singleGallery && it !in queue.byGallery }
            .toSet()
    }

    fun plan(
        entries: List<CatalogGallery>,
        requested: Collection<String>,
        queue: QueueStore.Snapshot?
    ): List<String> = plan(eligibleUrls(entries, GalleryCatalogProgress.index(queue)), requested)

    /** Keep selection order, remove canonical duplicates and ignore unavailable URLs. */
    fun plan(eligible: Set<String>, requested: Collection<String>): List<String> {
        return requested.asSequence()
            .mapNotNull(GalleryKeys::canonical)
            .filter { it in eligible }
            .distinct()
            .take(LIMIT)
            .toList()
    }
}
