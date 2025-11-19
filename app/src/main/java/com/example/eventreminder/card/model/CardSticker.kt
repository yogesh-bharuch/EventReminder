package com.example.eventreminder.card.model

// region Imports
import androidx.annotation.DrawableRes
import timber.log.Timber
// endregion

// region Constants
private const val TAG = "CardSticker"
// endregion

/**
 * CardSticker
 *
 * Represents a decorative sticker overlay for the card.
 * Examples: balloons, confetti, cake, stars, hearts, flowers.
 */
data class CardSticker(

    // Resource ID of the sticker drawable
    @DrawableRes val drawableResId: Int,

    // X/Y position of top-left corner
    val x: Float,
    val y: Float,

    // Optional scaling factor (1.0 = original size)
    val scale: Float = 1f,

    // Optional rotation in degrees
    val rotation: Float = 0f
) {

    init {
        Timber.tag(TAG).d("Sticker added: res=$drawableResId at ($x,$y)")
    }
}
