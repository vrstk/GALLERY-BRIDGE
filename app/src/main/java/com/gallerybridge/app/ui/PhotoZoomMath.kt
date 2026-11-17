package com.gallerybridge.app.ui

import kotlin.math.max
import kotlin.math.min

/** Bounds based on the *rendered* Fit image, not the surrounding black viewport. */
internal object PhotoZoomMath {
    const val MAX_SCALE = 6f
    data class Bounds(val x: Float, val y: Float)

    fun bounds(width: Float, height: Float, aspectRatio: Float, zoom: Float): Bounds {
        if (width <= 0f || height <= 0f || !width.isFinite() || !height.isFinite() ||
            aspectRatio <= 0f || !aspectRatio.isFinite() || !zoom.isFinite()) {
            return Bounds(0f, 0f)
        }
        val fittedWidth = min(width, height * aspectRatio)
        val fittedHeight = fittedWidth / aspectRatio
        val scale = zoom.coerceIn(1f, MAX_SCALE)
        return Bounds(
            ((fittedWidth * scale - width) / 2f).coerceAtLeast(0f),
            ((fittedHeight * scale - height) / 2f).coerceAtLeast(0f)
        )
    }

    /** Double-tap covers the viewport (with a little extra zoom), then taps reset to Fit. */
    fun doubleTapScale(width: Float, height: Float, aspectRatio: Float): Float {
        if (width <= 0f || height <= 0f || aspectRatio <= 0f ||
            !width.isFinite() || !height.isFinite() || !aspectRatio.isFinite()) return 2.5f
        val fittedWidth = min(width, height * aspectRatio)
        val fittedHeight = fittedWidth / aspectRatio
        return max(2f, max(width / fittedWidth, height / fittedHeight) * 1.05f)
            .coerceAtMost(MAX_SCALE)
    }
}
