package com.gallerybridge.app.ui

/**
 * Display-only preference. Switching modes must not modify selected photos,
 * uploaded files or transfer queues. LIST never creates preview image requests.
 */
enum class GalleryPreviewMode {
    MOSAIC, TILES, LIST;

    val preloadsImages: Boolean get() = this != LIST

    companion object {
        fun restore(value: String?): GalleryPreviewMode =
            entries.firstOrNull { it.name == value } ?: MOSAIC
    }
}
