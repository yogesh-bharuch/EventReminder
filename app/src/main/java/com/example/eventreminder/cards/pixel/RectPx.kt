package com.example.eventreminder.cards.pixel


// =============================================================
// RectPx.kt
// Small helper to represent rectangles in pixel coordinates.
// =============================================================

import android.graphics.RectF
import timber.log.Timber

private const val TAG = "RectPx"

/**
 * Simple rectangle in integer pixels.
 */
data class RectPx(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    init {
        if (width < 0 || height < 0) {
            Timber.tag(TAG).w("RectPx created with negative dimension: %s", this)
        }
    }

    fun toRectF(): RectF = RectF(x.toFloat(), y.toFloat(), (x + width).toFloat(), (y + height).toFloat())

    /** Center X in pixels */
    val centerX: Float get() = x + width / 2f

    /** Center Y in pixels */
    val centerY: Float get() = y + height / 2f

    /** Convert to human readable */
    override fun toString(): String = "RectPx(x=$x,y=$y,w=$width,h=$height)"
}
