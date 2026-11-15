package com.gallerybridge.app.core

/**
 * Photo-only presentation of discovered image URLs.
 * Default to one recommended URL per image family. Alternate resolutions and
 * secondary images remain available through the "all variants" control.
 * Display rules never alter the user's selected URLs or saved queues.
 */
object MediaResults {
    const val PAGE_SIZE = 30

    fun visible(
        all: List<Media>,
        showOthers: Boolean,
        recommendedUrls: Set<String>
    ): List<Media> {
        val photos = all.filter { it.kind == Kind.PHOTO }
        // Some sites expose only unusual image attributes: never show an empty
        // result when actual photos were discovered but no recommendation exists.
        return if (showOthers || recommendedUrls.isEmpty()) photos
            else photos.filter { it.url in recommendedUrls }
    }

    fun extraCount(all: List<Media>, recommendedUrls: Set<String>): Int =
        (all.count { it.kind == Kind.PHOTO } -
            visible(all, false, recommendedUrls).size).coerceAtLeast(0)

    /**
     * A filter may hide selected alternate resolutions. Never silently change
     * the pending transfer selection when toggling the visible photo variants.
     */
    fun hiddenSelectionCount(selectedUrls: Set<String>, visible: List<Media>): Int {
        if (selectedUrls.isEmpty()) return 0
        val visibleUrls = visible.mapTo(HashSet<String>(visible.size)) { it.url }
        return selectedUrls.count { it !in visibleUrls }
    }

    /** Show a bounded prefix in the alternate-variants view; never mutate all or selections. */
    fun page(all: List<Media>, limit: Int): List<Media> =
        all.take(limit.coerceIn(0, all.size))
}
