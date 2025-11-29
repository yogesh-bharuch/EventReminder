package com.example.eventreminder.cards.pixel

// =============================================================
// PixelRenderer.kt
// Central renderer that draws the card to either Compose DrawScope
// (via nativeCanvas) or android.graphics.Canvas (for PDF/bitmap).
// =============================================================

import android.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import timber.log.Timber
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "PixelRenderer"

/**
 * PixelRenderer: stateless renderer implementation.
 *
 * Responsibilities:
 *  - Draw background (solid color or bitmap)
 *  - Draw rounded card rect (clip)
 *  - Draw title / name text with pixel font sizes
 *  - Draw avatar bitmap with scale and rotation
 *  - Draw stickers (bitmaps or text) with transforms
 *
 * Note: text layout here is simple but extensible. For full text shaping use StaticLayout
 * (android.text.StaticLayout) for multi-line rendering. For simplicity and portability we
 * use canvas.drawText with manual measure and optional truncation.
 */
object PixelRenderer {

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(46, 0, 0, 0) // translucent overlay default
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        textAlign = Paint.Align.LEFT
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.DKGRAY
        textAlign = Paint.Align.LEFT
    }

    /**
     * Render into an Android Canvas (pixel-perfect).
     */
    fun renderToAndroidCanvas(androidCanvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        Timber.tag(TAG).d("renderToAndroidCanvas spec=%dx%d id=%d", spec.widthPx, spec.heightPx, data.reminderId)

        // Save/protect canvas state
        androidCanvas.save()
        try {
            // 1) Clip to rounded rect for card boundary
            val rectF = spec.backgroundLayer.toRectF()
            val clipPath = Path().apply {
                addRoundRect(rectF, spec.cornerRadiusPx.toFloat(), spec.cornerRadiusPx.toFloat(), Path.Direction.CW)
            }
            androidCanvas.clipPath(clipPath)

            // 2) Draw background (bitmap if available)
            if (data.backgroundBitmap != null) {
                val bg = data.backgroundBitmap
                val src = Rect(0, 0, bg.width, bg.height)
                val dst = Rect(0, 0, spec.widthPx, spec.heightPx)
                androidCanvas.drawBitmap(bg, src, dst, null)
                // slight overlay to improve foreground contrast
                androidCanvas.drawRect(0f, 0f, spec.widthPx.toFloat(), spec.heightPx.toFloat(), overlayPaint)
            } else {
                // default background fill
                androidCanvas.drawRect(0f, 0f, spec.widthPx.toFloat(), spec.heightPx.toFloat(), backgroundPaint)
            }

            // 3) Title
            if (data.showTitle) {
                drawTitle(androidCanvas, spec, data)
            }

            // 4) Name
            if (data.showName && !data.nameText.isNullOrBlank()) {
                drawName(androidCanvas, spec, data)
            }

            // 5) Avatar
            data.avatarBitmap?.let { drawAvatar(androidCanvas, spec, data) }

            // 6) Stickers
            drawStickers(androidCanvas, spec, data)

            // 7) Date row (bottom)
            drawDateRow(androidCanvas, spec, data)

        } finally {
            androidCanvas.restore()
        }
    }

    /**
     * Compose DrawScope entrypoint. Uses DrawScope.nativeCanvas back-end.
     */
    fun renderToDrawScope(drawScope: DrawScope, spec: CardSpecPx, data: CardDataPx) {
        Timber.tag(TAG).d("renderToDrawScope spec=%dx%d id=%d", spec.widthPx, spec.heightPx, data.reminderId)
        // Use the underlying android Canvas
        val native = drawScope.drawContext.canvas.nativeCanvas
        // We want to map canonical spec pixels to the DrawScope size.
        // drawScope.size is in logical pixels (Compose units). We compute a scale factor.
        val scaleX = drawScope.size.width / spec.widthPx.toFloat()
        val scaleY = drawScope.size.height / spec.heightPx.toFloat()
        // Maintain aspect by uniform scale (fit)
        val scale = minOf(scaleX, scaleY)

        native.save()
        native.scale(scale, scale)
        try {
            renderToAndroidCanvas(native, spec, data)
        } finally {
            native.restore()
        }
    }

    // ------------------------
    // Drawing helpers (basic)
    // ------------------------

    private fun drawTitle(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.titleBox
        // choose px size for title
        textPaint.textSize = 56f  // px
        textPaint.color = Color.BLACK
        val text = data.titleText
        val maxWidth = box.width.toFloat()
        val truncated = ellipsizeText(textPaint, text, maxWidth)
        // baseline approx: draw at box.y + textSize
        val x = box.x.toFloat()
        val y = box.y + textPaint.textSize
        canvas.drawText(truncated, x, y, textPaint)
    }

    private fun drawName(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.nameBox
        labelPaint.textSize = 40f
        labelPaint.color = Color.DKGRAY
        val text = data.nameText ?: ""
        val t = ellipsizeText(labelPaint, text, box.width.toFloat())
        val x = box.x.toFloat()
        val y = box.y + labelPaint.textSize
        canvas.drawText(t, x, y, labelPaint)
    }

    private fun drawDateRow(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.dateBox
        labelPaint.textSize = 34f
        labelPaint.color = Color.DKGRAY
        val left = data.originalDateLabel
        val right = data.nextDateLabel
        // left
        var x = box.x.toFloat()
        var y = box.y + labelPaint.textSize
        canvas.drawText(ellipsizeText(labelPaint, left, box.width * 0.6f), x, y, labelPaint)
        // right (align end)
        val rightText = ellipsizeText(labelPaint, right, box.width * 0.4f)
        val rightWidth = labelPaint.measureText(rightText)
        canvas.drawText(rightText, box.x + box.width - rightWidth.toFloat(), y, labelPaint)
    }

    private fun drawAvatar(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val bmp = data.avatarBitmap ?: return
        // avatar center px based on normalized transform
        val (cx, cy) = data.avatarCenterPx(spec)
        // base avatar size from spec.avatarBox
        val baseSize = spec.avatarBox.width
        val finalSize = (baseSize * data.avatarTransform.scale).toInt().coerceAtLeast(8)
        val half = finalSize / 2
        val left = cx - half
        val top = cy - half
        val dst = Rect(left, top, left + finalSize, top + finalSize)

        canvas.save()
        // apply rotation about center if any
        if (data.avatarTransform.rotationDeg != 0f) {
            canvas.rotate(data.avatarTransform.rotationDeg, cx.toFloat(), cy.toFloat())
        }
        // draw avatar bitmap with circle mask
        val shader = BitmapShader(Bitmap.createScaledBitmap(bmp, finalSize, finalSize, true), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        val radius = finalSize / 2f
        canvas.drawCircle(cx.toFloat(), cy.toFloat(), radius, paint)
        canvas.restore()
    }

    private fun drawStickers(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        // iterate and draw each sticker with transform
        data.stickers.forEach { s ->
            val bmp = s.bitmap
            val centerX = (s.xNorm * spec.widthPx).toInt()
            val centerY = (s.yNorm * spec.heightPx).toInt()
            val baseSize = (spec.widthPx * 0.15f).toInt() // default sticker scale relative to card width
            val size = (baseSize * s.scale).toInt().coerceAtLeast(8)
            val half = size / 2
            canvas.save()
            canvas.translate(centerX.toFloat(), centerY.toFloat())
            if (s.rotationDeg != 0f) {
                canvas.rotate(s.rotationDeg)
            }
            if (bmp != null) {
                val scaled = Bitmap.createScaledBitmap(bmp, size, size, true)
                canvas.drawBitmap(scaled, -half.toFloat(), -half.toFloat(), null)
            } else {
                // fallback: draw text box
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 32f
                    color = Color.BLACK
                }
                canvas.drawText(s.text ?: "?", -half + 8f, 8f, paint)
            }
            canvas.restore()
        }
    }

    // --------------------------
    // Utility: ellipsize text to fit width
    // --------------------------
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
