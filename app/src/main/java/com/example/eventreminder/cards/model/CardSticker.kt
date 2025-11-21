package com.example.eventreminder.cards.model

import androidx.annotation.DrawableRes

/**
 * Represents a sticker placed on the card.
 *
 * @param drawableResId Resource ID of the sticker image (PNG/WebP/SVG in res)
 * @param x  X position in dp
 * @param y  Y position in dp
 * @param scale Sticker size multiplier (1f = 100%)
 * @param rotation Rotation in degrees
 */
data class CardSticker(
    val id: Long = System.currentTimeMillis(),
    @DrawableRes val drawableResId: Int,
    var x: Float,
    var y: Float,
    var scale: Float = 1f,
    val rotation: Float = 0f
)
