package com.example.eventreminder.cards.pixel.stickers

import android.graphics.Bitmap

/**
 * =============================================================
 * StickerPx (Option-D Design)
 * -------------------------------------------------------------
 * Independent sticker layer used by:
 *  - StickerVM
 *  - PixelRenderer (free transform)
 *  - StickerHitTest (matrix-inverse selection)
 *
 * Supports 3 payload types:
 *  - drawableResId : Int?  (for images from resources)
 *  - bitmap        : Bitmap? (for custom sticker bitmaps)
 *  - text          : String? (for emoji/symbol)
 *
 * Only ONE of the three is expected to be non-null.
 * =============================================================
 */
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
