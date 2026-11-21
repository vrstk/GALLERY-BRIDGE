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

    fun eligible(entry: CatalogGallery, queue: QueueStore.Snapshot?): Boolean =
        entry.target.isBlank() &&
            GalleryKeys.canonical(entry.url) != null &&
            !(queue != null && GalleryKeys.same(entry.url, queue.sourceUrl))

    /** Keep visible/UI order, remove canonical duplicates and ignore unknown URLs. */
    fun plan(
        entries: List<CatalogGallery>,
        requested: Collection<String>,
        queue: QueueStore.Snapshot?
    ): List<String> {
        val eligible = entries.filter { eligible(it, queue) }
        val canonicalUrls = eligible.mapNotNull { GalleryKeys.canonical(it.url) }.toSet()
        return requested.asSequence()
            .mapNotNull(GalleryKeys::canonical)
            .filter { it in canonicalUrls }
            .distinct()
            .take(LIMIT)
            .toList()
    }
}
