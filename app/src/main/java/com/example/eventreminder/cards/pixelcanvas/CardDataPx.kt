package com.example.eventreminder.cards.pixelcanvas

// =============================================================
// CardDataPx.kt
// Pixel-oriented card data model — content + normalized transforms.
// =============================================================

import android.graphics.Bitmap
import androidx.core.graphics.toColorInt
import com.example.eventreminder.cards.pixelcanvas.stickers.model.StickerPx
import timber.log.Timber
import kotlin.math.roundToInt

private const val TAG = "CardDataPx"

/**
 * Avatar transform stored normalized.
 * xNorm / yNorm is the center position.
 */
data class AvatarTransformPx(
    val xNorm: Float = 0.5f,
    val yNorm: Float = 0.5f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f
)

/**
 * CardDataPx – authoritative pixel-based data model.
 * NOW using String UUID for reminderId.
 */
data class CardDataPx(
    val reminderId: String,               // <-- UUID now
    val titleText: String,
    val nameText: String?,
    val showTitle: Boolean = true,
    val showName: Boolean = true,

    // avatar
    val avatarBitmap: Bitmap? = null,
    val avatarTransform: AvatarTransformPx = AvatarTransformPx(),

    // background
    val backgroundBitmap: Bitmap? = null,

    // stickers
    val stickers: List<StickerPx> = emptyList(),

    // active sticker
    val activeStickerId: Long? = null,

    // labels
    val originalDateLabel: String = "",
    val nextDateLabel: String = "",
    val ageOrYearsLabel: String? = null,

    // text colors
    val titleColor: Int = "#222222".toColorInt(),
    val nameColor: Int = "#222222".toColorInt(),
    val originalDateColor: Int = "#222222".toColorInt()
) {

    init {
        Timber.tag(TAG).d(
            "CardDataPx created for id=%s title=%s",
            reminderId,
            titleText
        )
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
