package com.gallerybridge.app.core

import com.gallerybridge.app.transfer.QueueStore

/**
 * Cautious page-level duplicate guard. The catalog has no per-photo history,
 * so do not infer that a remote file exists or cloud sync is complete.
 * This policy never reads remote storage or mutates the queue.
 */
internal object GalleryTransferGuard {
    enum class Decision { CLEAR, REVIEW, BLOCKED }

    fun assess(
        pageUrl: String, catalog: List<CatalogGallery>, queue: QueueStore.Snapshot?,
        hasRecordedMatches: Boolean = false
    ): Decision {
        val canonical = GalleryKeys.canonical(pageUrl) ?: return Decision.CLEAR
        val sameQueue = queue?.takeIf { GalleryKeys.same(it.sourceUrl, canonical) }
        if (sameQueue != null && sameQueue.remaining > 0) return Decision.BLOCKED
        if (sameQueue != null && sameQueue.entries.isNotEmpty()) return Decision.REVIEW
        if (hasRecordedMatches) return Decision.REVIEW
        if (catalog.any { GalleryKeys.same(it.url, canonical) && it.target.isNotBlank() })
            return Decision.REVIEW
        return Decision.CLEAR
    }
}
