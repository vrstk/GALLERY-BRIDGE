package com.gallerybridge.app.core

/**
 * Bounded manual image inspection. The selector never modifies a download queue.
 */
object ImageProbePlan {
    const val MAX_FILES = 40

    fun candidates(media: List<Media>, selectedUrls: Set<String>): List<Media> =
        media.filter { it.kind == Kind.PHOTO && it.url in selectedUrls }.take(MAX_FILES)

    fun selectedPhotoCount(media: List<Media>, selectedUrls: Set<String>): Int =
        media.count { it.kind == Kind.PHOTO && it.url in selectedUrls }
}
