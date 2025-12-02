package com.example.eventreminder.cards.pixelcanvas.canvas

// =============================================================
// DrawStickers.kt
// - Sticker rendering (renderStickers / renderSingleSticker)
// - Sticker hit testing (findTopmostStickerUnderTouch / isTouchInsideSticker)
// - Kept exact math and scale rules from original PixelRenderer
// =============================================================

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import com.example.eventreminder.cards.pixelcanvas.StickerPx
import com.example.eventreminder.cards.pixelcanvas.stickers.engine.StickerBitmapCache
import com.example.eventreminder.cards.pixelcanvas.StickerPaint
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "DrawStickers"

object DrawStickers {

    private val stickerMatrix = Matrix()

    /**
     * Render all stickers in bottom→top order.
     * Uses same scale-boost, min-size rules as original implementation.
     */
    fun renderStickers(canvas: Canvas, spec: CardSpecPx, data: com.example.eventreminder.cards.pixelcanvas.CardDataPx) {
        val list = data.stickers
        if (list.isEmpty()) return

        list.forEach { sticker ->
            renderSingleSticker(canvas, spec, sticker)
        }
    }

    /**
     * Render a single sticker (drawable resource, custom bitmap, or text/emoji).
     *
     * This preserves the original rendering rules:
     *  - canvas.scale(sticker.scale * 3.5f) baseline boost
     *  - For drawable resources: additional boost and min-size enforcement
     *  - For bitmaps: draw centered
     *  - For text: use StickerPaint.textPaint and measured bounds
     */
    private fun renderSingleSticker(canvas: Canvas, spec: CardSpecPx, sticker: StickerPx) {
        val cx = sticker.xNorm * spec.widthPx
        val cy = sticker.yNorm * spec.heightPx

        canvas.save()

        // Move to sticker center and rotate
        canvas.translate(cx, cy)
        canvas.rotate(sticker.rotationDeg)

        // BOOST SIZE → easier for gestures
        val scaleBoost = 3.5f
        canvas.scale(sticker.scale * scaleBoost, sticker.scale * scaleBoost)

        when {
            // A) Drawable resource path (with min-size boost)
            sticker.drawableResId != null -> {
                val bmp = StickerBitmapCache.getBitmap(sticker.drawableResId!!)
                if (bmp != null) {
                    val drawableBoost = 3.0f
                    val baseScale = sticker.scale * drawableBoost

                    val minSize = 180f
                    val minScale = minSize / bmp.width.toFloat()

                    val finalScale = max(baseScale, minScale)

                    canvas.scale(finalScale, finalScale)
                    canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, null)
                }
            }

            // B) Custom bitmap picked by user
            sticker.bitmap != null -> {
                val bmp = sticker.bitmap!!
                val halfW = bmp.width / 2f
                val halfH = bmp.height / 2f
                canvas.drawBitmap(bmp, -halfW, -halfH, null)
            }

            // C) Emoji/text case
            sticker.text != null -> {
                val paint = StickerPaint.textPaint
                val bounds = StickerPaint.getTextBounds(sticker.text!!)
                canvas.drawText(
                    sticker.text!!,
                    -bounds.centerX().toFloat(),
                    -bounds.centerY().toFloat(),
                    paint
                )
            }
        }

        canvas.restore()
    }

    // ---------------------------------------------------------
    // HIT TESTING (matches renderSingleSticker sizing exactly)
    // ---------------------------------------------------------

    /**
     * Find topmost sticker under the touch point (iterates reverse order).
     */
    fun findTopmostStickerUnderTouch(
        touchX: Float,
        touchY: Float,
        spec: CardSpecPx,
        data: com.example.eventreminder.cards.pixelcanvas.CardDataPx
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
     * Matrix-aware hit test for a sticker.
     * Preserves the exact effectiveScale calculation used in rendering.
     */
    fun isTouchInsideSticker(
        touchX: Float,
        touchY: Float,
        spec: CardSpecPx,
        sticker: StickerPx
    ): Boolean {

        // sticker center (in card px)
        val cx = sticker.xNorm * spec.widthPx
        val cy = sticker.yNorm * spec.heightPx

        // Fetch bitmap if available
        val bmpForSize: Bitmap? = when {
            sticker.drawableResId != null -> StickerBitmapCache.getBitmap(sticker.drawableResId!!)
            sticker.bitmap != null -> sticker.bitmap
            else -> null
        }

        val effectiveScale: Float = if (sticker.drawableResId != null && bmpForSize != null) {
            // drawable sticker path
            val scaleBoostInitial = 3.5f
            val drawableBoost = 3.0f
            val baseScale = sticker.scale * drawableBoost

            val minSize = 180f
            val minScale = minSize / bmpForSize.width.toFloat()

            val finalScale = max(baseScale, minScale)

            // total effective scale applied to bitmap when drawing
            sticker.scale * scaleBoostInitial * finalScale
        } else {
            // For custom bitmap or text, render applies only the initial boost
            val scaleBoostInitial = 3.5f
            sticker.scale * scaleBoostInitial
        }

        // Determine native width/height
        val nativeW: Float
        val nativeH: Float
        if (bmpForSize != null) {
            nativeW = bmpForSize.width.toFloat()
            nativeH = bmpForSize.height.toFloat()
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

        // Rotate touch point into sticker-local coordinates
        val dx = touchX - cx
        val dy = touchY - cy

        val r = Math.toRadians(sticker.rotationDeg.toDouble())
        val cosR = cos(r)
        val sinR = sin(r)

        val rx = (dx * cosR + dy * sinR).toFloat()
        val ry = (-dx * sinR + dy * cosR).toFloat()

        return (rx > -halfW && rx < halfW && ry > -halfH && ry < halfH)
    }
}
