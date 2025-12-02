package com.example.eventreminder.cards.pixel.stickers

import android.graphics.Bitmap


data class StickerPx(

    // Unique ID so gestures + long-press delete can target the right sticker
    val id: Long,

    // ---- Payload Types (pick one) ----
    val drawableResId: Int? = null,
    val bitmap: Bitmap? = null,
    val text: String? = null,

    // ---- Normalized Position (0f..1f of card area) ----
    // Default bottom-left corner where new sticker appears
    val xNorm: Float = 0.15f,
    val yNorm: Float = 0.85f,

    // ---- Transform Values ----
    val scale: Float = 1f,
    val rotationDeg: Float = 0f
)
