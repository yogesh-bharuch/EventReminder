package com.example.eventreminder.cards.pixel

// =============================================================
// PixelRenderer.kt (updated for 1080x1200 tall card + gradient BG)
// Adds drawing of backgroundBitmap (if provided) before content.
// Draw order:
//  1) clip rounded rect
//  2) draw backgroundBitmap OR gradient fallback
//  3) subtle overlay
//  4) title / name / middle border / avatar / stickers / dates
// =============================================================

import android.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import timber.log.Timber
import kotlin.math.roundToInt

private const val TAG = "PixelRenderer"

object PixelRenderer {

    // Gradient colors (BG3)
    private val defaultBgTopColor = Color.parseColor("#FFFDE7") // soft warm top
    private val defaultBgBottomColor = Color.parseColor("#FFF0F4") // subtle bottom tint

    private fun makePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Render to Android Canvas (pixel-accurate)
     *
     * This function draws the card into the provided Android Canvas using the canonical
     * pixel spec. It now supports an optional background bitmap stored in CardDataPx.backgroundBitmap.
     */
    fun renderToAndroidCanvas(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        Timber.tag(TAG).d("render -> id=%d spec=%dx%d", data.reminderId, spec.widthPx, spec.heightPx)
        canvas.save()
        try {
            // -------------------------
            // Clip to rounded rect (card shape)
            // -------------------------
            val rectF = spec.backgroundLayer.toRectF()
            val path = Path().apply {
                addRoundRect(rectF, spec.cornerRadiusPx.toFloat(), spec.cornerRadiusPx.toFloat(), Path.Direction.CW)
            }
            canvas.clipPath(path)

            // -------------------------
            // BACKGROUND: image if provided, otherwise gradient fallback
            // -------------------------
            if (data.backgroundBitmap != null) {
                try {
                    val bmp = data.backgroundBitmap
                    val dest = Rect(0, 0, spec.widthPx, spec.heightPx)
                    val paint = makePaint().apply {
                        isFilterBitmap = true
                    }
                    // draw scaled bitmap to cover the entire card area
                    canvas.drawBitmap(bmp, null, dest, paint)
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "Failed drawing background bitmap; falling back to gradient")
                    // fallback to gradient below
                    drawGradientBackground(canvas, spec)
                }
            } else {
                // no background bitmap → draw the gradient
                drawGradientBackground(canvas, spec)
            }

            // Slight inner overlay to soften edges and unify photo/gradient appearance
            val overlay = makePaint().apply {
                color = Color.argb(18, 0, 0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, spec.widthPx.toFloat(), spec.heightPx.toFloat(), overlay)

            // -------------------------
            // Foreground content
            // -------------------------
            drawTitle(canvas, spec, data)
            drawName(canvas, spec, data)
            drawMiddleBorder(canvas, spec)
            data.avatarBitmap?.let { drawAvatar(canvas, spec, data) }
            drawStickers(canvas, spec, data)
            drawDateRow(canvas, spec, data)

        } finally {
            canvas.restore()
        }
    }

    private fun drawGradientBackground(canvas: Canvas, spec: CardSpecPx) {
        val shader = LinearGradient(
            0f, 0f, 0f, spec.heightPx.toFloat(),
            intArrayOf(defaultBgTopColor, defaultBgBottomColor),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        val bgPaint = makePaint().apply {
            this.shader = shader
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, spec.widthPx.toFloat(), spec.heightPx.toFloat(), bgPaint)
    }

    /**
     * Compose DrawScope entrypoint - maps draw scope size to spec scale and calls Android renderer.
     */
    fun renderToDrawScope(drawScope: DrawScope, spec: CardSpecPx, data: CardDataPx) {
        val native = drawScope.drawContext.canvas.nativeCanvas
        val scaleX = drawScope.size.width / spec.widthPx.toFloat()
        val scaleY = drawScope.size.height / spec.heightPx.toFloat()
        // Use uniform scale to keep aspect
        val scale = minOf(scaleX, scaleY)
        native.save()
        native.scale(scale, scale)
        try {
            renderToAndroidCanvas(native, spec, data)
        } finally {
            native.restore()
        }
    }

    /* -------------------------
       Drawing helper methods
       ------------------------- */

    private fun drawTitle(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.titleBox
        val p = makePaint().apply {
            color = Color.parseColor("#222222")
            textSize = 72f   // px for 1080 base
            isFakeBoldText = true
        }
        val text = data.titleText
        val maxWidth = box.width.toFloat()
        val txt = ellipsizeText(p, text, maxWidth)
        // baseline approx: box.y + textSize - small padding
        val x = box.x.toFloat()
        val y = box.y + p.textSize - 12f
        canvas.drawText(txt, x, y, p)
    }

    private fun drawName(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.nameBox
        val p = makePaint().apply {
            color = Color.parseColor("#444444")
            textSize = 48f
        }
        val text = data.nameText ?: ""
        val txt = ellipsizeText(p, text, box.width.toFloat())
        val x = box.x.toFloat()
        val y = box.y + p.textSize - 6f
        canvas.drawText(txt, x, y, p)
    }

    private fun drawMiddleBorder(canvas: Canvas, spec: CardSpecPx) {
        val box = spec.middleBox
        val p = makePaint().apply {
            color = Color.argb(30, 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val r = RectF(box.x.toFloat(), box.y.toFloat(), (box.x + box.width).toFloat(), (box.y + box.height).toFloat())
        canvas.drawRoundRect(r, 12f, 12f, p)
    }

    private fun drawDateRow(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.dateBox
        val p = makePaint().apply {
            color = Color.parseColor("#666666")
            textSize = 40f
        }
        val left = data.originalDateLabel
        val right = data.nextDateLabel
        val y = box.y + p.textSize
        // left
        val leftText = ellipsizeText(p, left, box.width * 0.6f)
        canvas.drawText(leftText, box.x.toFloat(), y, p)
        // right aligned
        val rightText = ellipsizeText(p, right, box.width * 0.4f)
        val rightWidth = p.measureText(rightText)
        canvas.drawText(rightText, (box.x + box.width - rightWidth).toFloat(), y, p)
    }

    private fun drawAvatar(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val bmp = data.avatarBitmap ?: return
        // default avatar center: somewhere in the upper-middle of middleBox
        val centerX = (spec.widthPx * (data.avatarTransform.xNorm)).roundToInt()
        val centerY = (spec.heightPx * (data.avatarTransform.yNorm)).roundToInt()
        val baseSize = (spec.widthPx * 0.18f).toInt()
        val finalSize = (baseSize * data.avatarTransform.scale).toInt().coerceAtLeast(8)
        val half = finalSize / 2
        val left = centerX - half
        val top = centerY - half

        canvas.save()
        if (data.avatarTransform.rotationDeg != 0f) {
            canvas.rotate(data.avatarTransform.rotationDeg, centerX.toFloat(), centerY.toFloat())
        }
        val scaled = Bitmap.createScaledBitmap(bmp, finalSize, finalSize, true)
        val paint = makePaint()
        // circular mask draw
        val shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.shader = shader
        canvas.drawCircle(centerX.toFloat(), centerY.toFloat(), finalSize / 2f, paint)
        canvas.restore()
    }

    private fun drawStickers(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        data.stickers.forEach { s ->
            val cx = (s.xNorm * spec.widthPx).toInt()
            val cy = (s.yNorm * spec.heightPx).toInt()
            val base = (spec.widthPx * 0.12f).toInt()
            val size = (base * s.scale).toInt().coerceAtLeast(8)
            val half = size / 2
            canvas.save()
            canvas.translate(cx.toFloat(), cy.toFloat())
            if (s.rotationDeg != 0f) canvas.rotate(s.rotationDeg)
            if (s.bitmap != null) {
                val bmp = Bitmap.createScaledBitmap(s.bitmap, size, size, true)
                canvas.drawBitmap(bmp, -half.toFloat(), -half.toFloat(), null)
            } else {
                val p = makePaint().apply {
                    color = Color.parseColor("#222222")
                    textSize = 28f
                }
                val txt = if (!s.text.isNullOrBlank()) s.text else "◻"
                canvas.drawText(txt, -half + 8f, 8f, p)
            }
            canvas.restore()
        }
    }

    /* --------------------------
       Utilities
       -------------------------- */

    private fun ellipsizeText(p: Paint, text: String, maxWidth: Float): String {
        val measured = p.measureText(text)
        if (measured <= maxWidth) return text
        val ell = "…"
        var end = text.length
        while (end > 0) {
            val sub = text.substring(0, end) + ell
            if (p.measureText(sub) <= maxWidth) return sub
            end--
        }
        return ell
    }
}
