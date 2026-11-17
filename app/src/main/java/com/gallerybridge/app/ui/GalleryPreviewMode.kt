package com.gallerybridge.app.ui

/** The gallery always uses the justified photo mosaic, including legacy installs. */
enum class GalleryPreviewMode {
    MOSAIC;

    val preloadsImages: Boolean get() = true

    companion object {
        fun restore(value: String?): GalleryPreviewMode = MOSAIC
    }
}
