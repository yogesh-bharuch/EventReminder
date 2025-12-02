package com.example.eventreminder.cards.pixelcanvas

// =============================================================
// CardDataPx.kt
// Pixel-oriented card data model — content + normalized transforms.
// =============================================================

import android.graphics.Bitmap
import timber.log.Timber
import kotlin.math.roundToInt

private const val TAG = "CardDataPx"

/**
 * Sticker representation for pixel renderer.
 *
 * Positions are stored as normalized coordinates (0f..1f) relative to canonical card size
 * so they remain scale independent.
 */
data class StickerPx(
    val id: Long,
    val drawableResId: Int?,    // integer drawable resource (optional)
    val bitmap: Bitmap?,        // bitmap (preferred)
    val text: String?,          // fallback text sticker
    val xNorm: Float,           // 0..1 (left origin)
    val yNorm: Float,           // 0..1 (top origin)
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
)

/**
 * Avatar transform stored normalized.
 * xNorm / yNorm is the center position.
 *
 * These defaults place the avatar in a safe, visible area.
 */
data class AvatarTransformPx(
    val xNorm: Float = 0.5f,      // center of bitmap in 0..1 space
    val yNorm: Float = 0.5f,
    val scale: Float = 1f,        // multiply bitmap
    val rotationDeg: Float = 0f   // rotate around center
)

/**
 * CardDataPx – the authoritative data model used by PixelRenderer.
 */
data class CardDataPx(
    val reminderId: Long,
    val titleText: String,
    val nameText: String?,
    val showTitle: Boolean = true,
    val showName: Boolean = true,

    // avatar
    val avatarBitmap: Bitmap? = null,
    val avatarTransform: AvatarTransformPx = AvatarTransformPx(),

    // background
    val backgroundBitmap: Bitmap? = null,

    // stickers layer
    val stickers: List<StickerPx> = emptyList(),

    // 🔥 ACTIVE STICKER ID (for delete button inside Canvas)
    val activeStickerId: Long? = null,

    // labels
    val originalDateLabel: String = "",
    val nextDateLabel: String = "",
    val ageOrYearsLabel: String? = null
) {
    init {
        Timber.tag(TAG).d("CardDataPx created for id=%d title=%s", reminderId, titleText)
    }

    /**
     * Compute absolute px coordinates for avatar center based on spec.
     */
    fun avatarCenterPx(spec: CardSpecPx): Pair<Int, Int> {
        val cx = (avatarTransform.xNorm * spec.widthPx).roundToInt()
        val cy = (avatarTransform.yNorm * spec.heightPx).roundToInt()
        return cx to cy
    }
}
