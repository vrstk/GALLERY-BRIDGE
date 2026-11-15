package com.gallerybridge.app.core

/**
 * A row-based justified photo layout. Dimensions are in density-independent
 * logical units, so the calculation can be tested without Android UI classes.
 *
 * Once an image loads, its real aspect ratio replaces the temporary estimate.
 * The final incomplete row retains its natural height instead of being
 * stretched across the screen.
 */
object JustifiedRows {
    data class Row(
        val photos: List<Media>,
        val widths: List<Float>,
        val height: Float
    )

    fun pack(
        photos: List<Media>,
        aspectRatios: Map<String, Float>,
        availableWidth: Float,
        targetHeight: Float = 152f,
        spacing: Float = 5f,
        minHeight: Float = 95f,
        maxHeight: Float = 235f
    ): List<Row> {
        if (photos.isEmpty() || availableWidth <= 1f) return emptyList()
        val safeGap = spacing.coerceAtLeast(0f)
        val desiredHeight = targetHeight.coerceAtLeast(48f)
        val rows = mutableListOf<Row>()
        val pending = mutableListOf<Media>()
        var ratioSum = 0f

        fun ratio(item: Media): Float {
            val value = aspectRatios[item.url] ?: 0.82f
            return if (value.isFinite() && value > 0f) value.coerceIn(0.35f, 4.0f) else 0.82f
        }

        fun finish(last: Boolean) {
            if (pending.isEmpty()) return
            val widthForImages = (availableWidth - safeGap * (pending.size - 1)).coerceAtLeast(1f)
            val idealHeight = widthForImages / ratioSum.coerceAtLeast(0.1f)
            // An unusually wide photo can require a shorter row to fit.
            // Prevent sideways clipping instead of enforcing minHeight.
            val height = if (last) {
                minOf(desiredHeight, idealHeight).coerceAtLeast(1f)
            } else {
                idealHeight.coerceAtMost(maxHeight.coerceAtLeast(1f)).coerceAtLeast(1f)
            }
            // Never crop; each tile's ratio determines its width.
            val widths = pending.map { (ratio(it) * height).coerceAtLeast(1f) }
            rows += Row(pending.toList(), widths, height)
            pending.clear()
            ratioSum = 0f
        }

        for (photo in photos) {
            val nextRatio = ratio(photo)
            pending += photo
            ratioSum += nextRatio
            val estimatedWidth = ratioSum * desiredHeight + safeGap * (pending.size - 1)
            if (estimatedWidth >= availableWidth) finish(last = false)
        }
        finish(last = true)
        return rows
    }
}
