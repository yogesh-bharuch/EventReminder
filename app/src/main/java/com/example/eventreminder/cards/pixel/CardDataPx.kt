package com.example.eventreminder.cards.pixel

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
 * so they remain scale independent. The renderer will convert to absolute px using the spec.
 */
data class StickerPx(
    val id: Long,
    val drawableResId: Int?,    // optional integer resource id (if used)
    val bitmap: Bitmap?,        // optional bitmap (preferred if available)
    val text: String?,
    val xNorm: Float,           // 0..1 (left origin)
    val yNorm: Float,           // 0..1 (top origin)
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
)

/**
 * Avatar transform stored normalized.
 * xNorm / yNorm is the center position.
 */
data class AvatarTransformPx(
    val xNorm: Float = 0.9f,
    val yNorm: Float = 0.15f,
    val scale: Float = 1.0f,
    val rotationDeg: Float = 0f,
)

/**
 * Card data used by PixelRenderer.
 * Keep minimal fields required for rendering.
 */
data class CardDataPx(
    val reminderId: Long,
    val titleText: String,
    val nameText: String?,
    val showTitle: Boolean = true,
    val showName: Boolean = true,
    val avatarBitmap: Bitmap? = null,
    val avatarTransform: AvatarTransformPx = AvatarTransformPx(),
    val backgroundBitmap: Bitmap? = null,
    val stickers: List<StickerPx> = emptyList(),
    val originalDateLabel: String = "",
    val nextDateLabel: String = "",
    val ageOrYearsLabel: String? = null
) {
    init {
        Timber.tag(TAG).d("CardDataPx created for id=%d title=%s", reminderId, titleText)
    }

    /**
     * Helper: compute absolute px for avatar box center and size given spec.
     */
    fun avatarCenterPx(spec: CardSpecPx): Pair<Int, Int> {
        val cx = (avatarTransform.xNorm * spec.widthPx).roundToInt()
        val cy = (avatarTransform.yNorm * spec.heightPx).roundToInt()
        return cx to cy
    }
}
