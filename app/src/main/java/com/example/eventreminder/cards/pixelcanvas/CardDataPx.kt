package com.example.eventreminder.cards.pixelcanvas

// =============================================================
// CardDataPx.kt
// Pixel-oriented card data model — content + normalized transforms.
// =============================================================

import android.graphics.Bitmap
import android.graphics.Color
import timber.log.Timber
import kotlin.math.roundToInt
import com.example.eventreminder.cards.pixelcanvas.stickers.model.StickerPx
import androidx.core.graphics.toColorInt

private const val TAG = "CardDataPx"

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
    //val titleColor: Int = "#222222".toColorInt(),
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
    val ageOrYearsLabel: String? = null,

    val titleColor: Int = "#222222".toColorInt(),
    val nameColor: Int = "#222222".toColorInt(),
    val originalDateColor: Int = "#222222".toColorInt()

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
