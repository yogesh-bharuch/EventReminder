package com.example.eventreminder.cards.pixelcanvas.editor

import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import com.example.eventreminder.cards.pixelcanvas.StickerPaint
import com.example.eventreminder.cards.pixelcanvas.stickers.engine.StickerBitmapCache
import com.example.eventreminder.cards.pixelcanvas.stickers.model.StickerPx
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * TouchStickerUtils
 *
 * Exact hit-testing for stickers, matching the rendered size.
 */
object TouchStickerUtils {

    /**
     * Find the topmost sticker under touch (reverse order lookup).
     */
    fun findTopmostStickerUnderTouch(
        touchX: Float,
        touchY: Float,
        spec: CardSpecPx,
        data: CardDataPx
    ): StickerPx? {
        for (i in data.stickers.indices.reversed()) {
            val sticker = data.stickers[i]
            if (isTouchInsideSticker(touchX, touchY, spec, sticker)) {
                return sticker
            }
        }
        return null
    }

    /**
     * Exact pixel-perfect hit test for ONE sticker.
     * Mirrors the same scale rules used during rendering.
     */
    fun isTouchInsideSticker(
        touchX: Float,
        touchY: Float,
        spec: CardSpecPx,
        sticker: StickerPx
    ): Boolean {

        val centerX = sticker.xNorm * spec.widthPx
        val centerY = sticker.yNorm * spec.heightPx

        // Resolve sticker bitmap or text bounds
        val bmp = when {
            sticker.drawableResId != null -> StickerBitmapCache.getBitmap(sticker.drawableResId!!)
            sticker.bitmap != null -> sticker.bitmap
            else -> null
        }

        val effectiveScale: Float =
            if (sticker.drawableResId != null && bmp != null) {
                val scaleBoostInitial = 3.5f
                val drawableBoost = 3.0f
                val baseScale = sticker.scale * drawableBoost

                val minSize = 180f
                val minScale = minSize / bmp.width.toFloat()

                val finalScale = max(baseScale, minScale)

                sticker.scale * scaleBoostInitial * finalScale
            } else {
                sticker.scale * 3.5f
            }

        val nativeW: Float
        val nativeH: Float

        if (bmp != null) {
            nativeW = bmp.width.toFloat()
            nativeH = bmp.height.toFloat()
        } else if (sticker.text != null) {
            val bounds = StickerPaint.getTextBounds(sticker.text!!)
            nativeW = bounds.width().toFloat()
            nativeH = bounds.height().toFloat()
        } else {
            val fallback = spec.widthPx * 0.12f
            nativeW = fallback
            nativeH = fallback
        }

        val halfW = (nativeW * effectiveScale) / 2f
        val halfH = (nativeH * effectiveScale) / 2f

        // Rotate touch into sticker's local coordinate space
        val dx = touchX - centerX
        val dy = touchY - centerY

        val rad = Math.toRadians(sticker.rotationDeg.toDouble())
        val cosR = cos(rad)
        val sinR = sin(rad)

        val localX = (dx * cosR + dy * sinR).toFloat()
        val localY = (-dx * sinR + dy * cosR).toFloat()

        return (localX > -halfW && localX < halfW &&
                localY > -halfH && localY < halfH)
    }
}