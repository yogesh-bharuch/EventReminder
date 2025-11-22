package com.example.eventreminder.card.model

// =============================================================
// Imports
// =============================================================
import androidx.annotation.DrawableRes
import timber.log.Timber

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardSticker"

/**
 * CardSticker
 *
 * Represents a decorative sticker placed on the card canvas.
 * These stickers are optional overlays such as:
 * - balloons
 * - confetti
 * - stars
 * - hearts
 * - flowers
 * - cake icons
 *
 * The renderer uses this information to place, scale, and rotate
 * the sticker bitmap during card generation.
 */
data class CardSticker(

    /** Resource ID of the drawable to render as a sticker */
    @DrawableRes val drawableResId: Int,

    /** X-coordinate (top-left) where the sticker will be drawn */
    val x: Float,

    /** Y-coordinate (top-left) where the sticker will be drawn */
    val y: Float,

    /** Scaling factor (1.0 = original size, 2.0 = double) */
    val scale: Float = 1f,

    /** Rotation angle in degrees (0 = normal, 45 = tilted) */
    val rotation: Float = 0f
) {

    init {
        Timber.tag(TAG).d(
            "Sticker created: resId=$drawableResId  pos=($x,$y)  scale=$scale  rotation=$rotation"
        )
    }
}
