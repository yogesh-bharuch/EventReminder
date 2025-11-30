package com.example.eventreminder.cards.pixel

// =============================================================
// PixelRenderer.kt — Free Avatar Layer (Sticker-like)
// - Avatar = free movable/zoomable/rotatable layer
// - NO circle mask, NO cropping, NO radius
// - Placeholder = camera icon (style C)
// - Uses Matrix for full control
// - Clips only the CARD SHAPE, not the avatar
// =============================================================

import android.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "PixelRenderer"

object PixelRenderer {

    // Gradient colors
    private val bgTop = android.graphics.Color.parseColor("#FFFDE7")
    private val bgBottom = android.graphics.Color.parseColor("#FFF0F4")

    private fun p() = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---------------------------------------------------------
    // PUBLIC ENTRY — Compose DrawScope
    // ---------------------------------------------------------
    fun renderToDrawScope(ds: DrawScope, spec: CardSpecPx, data: CardDataPx) {
        val canvas = ds.drawContext.canvas.nativeCanvas
        val sx = ds.size.width / spec.widthPx.toFloat()
        val sy = ds.size.height / spec.heightPx.toFloat()
        val scale = min(sx, sy)

        canvas.save()
        canvas.scale(scale, scale)
        try {
            renderToAndroidCanvas(canvas, spec, data)
        } finally {
            canvas.restore()
        }
        }


    /**
     * Main renderer entry.
     * Draw order:
     *   1. Clip to rounded card
     *   2. Background (bitmap or gradient)
     *   3. Subtle overlay
     *   4. Foreground text + stickers
     *   5. FREE-AVATAR (photo moves freely, not circular)
     */
    fun renderToAndroidCanvas(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {

        Timber.tag(TAG).d("renderToAndroidCanvas START")

        // ---------------------------------------------------------
        // 1) Clip to card rounded rectangle
        // ---------------------------------------------------------
        canvas.save()
        val rectF = spec.backgroundLayer.toRectF()
        val clipPath = Path().apply {
            addRoundRect(
                rectF,
                spec.cornerRadiusPx.toFloat(),
                spec.cornerRadiusPx.toFloat(),
                Path.Direction.CW
            )
        }
        canvas.clipPath(clipPath)

        // ---------------------------------------------------------
        // 2) Background (bitmap or fallback gradient)
        // ---------------------------------------------------------
        val bg = data.backgroundBitmap
        if (bg != null) {
            try {
                canvas.drawBitmap(
                    bg,
                    null,
                    Rect(0, 0, spec.widthPx, spec.heightPx),
                    p()
                )
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "BG draw failed → fallback gradient")
                drawGradient(canvas, spec)
            }
        } else {
            drawGradient(canvas, spec)
        }

        // ---------------------------------------------------------
        // 3) Subtle overlay tint (for consistent appearance)
        // ---------------------------------------------------------
        canvas.drawRect(
            0f, 0f, spec.widthPx.toFloat(), spec.heightPx.toFloat(),
            p().apply { color = android.graphics.Color.argb(18, 0, 0, 0) }
        )

        // ---------------------------------------------------------
        // 4) Foreground text layers
        // ---------------------------------------------------------
        drawTitle(canvas, spec, data)
        drawName(canvas, spec, data)

        // Stickers (independent free-floating objects)
        drawStickers(canvas, spec, data)

        // Date row
        drawDateRow(canvas, spec, data)

        // ---------------------------------------------------------
        // 5) FREE-AVATAR LAYER (NEW)
        // No circular mask.
        // Photo is rendered just like a free-move sticker:
        // - Pan anywhere
        // - Zoom freely
        // - Rotate
        // - Position can move outside clip bounds
        // ---------------------------------------------------------
        drawAvatarFree(canvas, spec, data)

        // ---------------------------------------------------------
        // Restore canvas clipping
        // ---------------------------------------------------------
        canvas.restore()

        Timber.tag(TAG).d("renderToAndroidCanvas DONE")
    }


    // ---------------------------------------------------------
    // Gradient Background
    // ---------------------------------------------------------
    private fun drawGradient(canvas: Canvas, spec: CardSpecPx) {
        val shader = LinearGradient(
            0f, 0f,
            0f, spec.heightPx.toFloat(),
            intArrayOf(bgTop, bgBottom),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(
            0f, 0f,
            spec.widthPx.toFloat(), spec.heightPx.toFloat(),
            p().apply { this.shader = shader }
        )
    }


    // ---------------------------------------------------------
    // Title
    // ---------------------------------------------------------
    private fun drawTitle(c: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.titleBox
        val paint = p().apply {
            color = android.graphics.Color.parseColor("#222222")
            textSize = 72f
            isFakeBoldText = true
        }
        val t = ellipsize(paint, data.titleText, box.width.toFloat())
        c.drawText(t, box.x.toFloat(), box.y + paint.textSize - 12f, paint)
    }

    private fun drawName(c: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.nameBox
        val paint = p().apply {
            color = android.graphics.Color.parseColor("#444444")
            textSize = 48f
        }
        val t = ellipsize(paint, data.nameText ?: "", box.width.toFloat())
        c.drawText(t, box.x.toFloat(), box.y + paint.textSize - 6f, paint)
    }


    // ---------------------------------------------------------
    // STICKERS
    // ---------------------------------------------------------
    private fun drawStickers(c: Canvas, spec: CardSpecPx, data: CardDataPx) {
        data.stickers.forEach { s ->
            val cx = (s.xNorm * spec.widthPx)
            val cy = (s.yNorm * spec.heightPx)
            val base = (spec.widthPx * 0.12f).toInt()
            val size = (base * s.scale).toInt().coerceAtLeast(8)
            val half = size / 2

            c.save()
            c.translate(cx, cy)
            if (s.rotationDeg != 0f) c.rotate(s.rotationDeg)

            if (s.bitmap != null) {
                val scaled = Bitmap.createScaledBitmap(s.bitmap, size, size, true)
                c.drawBitmap(scaled, -half.toFloat(), -half.toFloat(), null)
            } else {
                val paint = p().apply { color = android.graphics.Color.parseColor("#222222"); textSize = 28f }
                c.drawText(s.text ?: "◻", -half + 8f, 8f, paint)
            }

            c.restore()
        }
    }


    // ---------------------------------------------------------
    // DATE ROW
    // ---------------------------------------------------------
    private fun drawDateRow(c: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.dateBox
        val paint = p().apply { color = android.graphics.Color.parseColor("#666666"); textSize = 40f }
        val y = box.y + paint.textSize

        val left = ellipsize(paint, data.originalDateLabel, box.width * 0.6f)
        c.drawText(left, box.x.toFloat(), y, paint)

        val right = ellipsize(paint, data.nextDateLabel, box.width * 0.4f)
        val wr = paint.measureText(right)
        c.drawText(right, (box.x + box.width - wr).toFloat(), y, paint)
    }


    // ---------------------------------------------------------
    // AVATAR (FREE TRANSFORM LAYER)  ⭐⭐
    // ---------------------------------------------------------
    fun drawAvatarFree(c: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val bmp = data.avatarBitmap

        // Compute avatar center and transform
        val cx = spec.widthPx * data.avatarTransform.xNorm
        val cy = spec.heightPx * data.avatarTransform.yNorm
        val s = data.avatarTransform.scale.coerceIn(0.1f, 8f)
        val rot = data.avatarTransform.rotationDeg

        // Default avatar size (base set from bitmap or placeholder)
        val baseSize = bmp?.width ?: (spec.widthPx * 0.20f).toInt()
        val avatarSize = baseSize * s
        val radius = avatarSize / 2f

        // -------------------------------------------------------
        // CASE A — NO PHOTO → draw circular camera placeholder
        // -------------------------------------------------------
        if (bmp == null) {
            drawCameraPlaceholderStatic(c, spec)
            return
        }

        // -------------------------------------------------------
        // CASE B — REAL PHOTO → circular masked free-moving image
        // -------------------------------------------------------
        val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        val mat = Matrix().apply {
            // Center bitmap origin
            postTranslate(-bmp.width / 2f, -bmp.height / 2f)

            // Scale at center
            postScale(s, s)

            // Rotate at center
            postRotate(rot)

            // Move to card position
            postTranslate(cx, cy)
        }

        shader.setLocalMatrix(mat)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
        }

        c.save()
        c.drawCircle(cx, cy, radius, paint)
        c.restore()
    }


    // ---------------------------------------------------------
    // PLACEHOLDER (Camera Icon, style C)
    // ---------------------------------------------------------
    private fun drawCameraPlaceholder(c: Canvas, spec: CardSpecPx) {
        val cx = spec.widthPx / 2f
        val cy = spec.heightPx / 2f
        val size = min(spec.widthPx, spec.heightPx) * 0.25f
        val half = size / 2f

        // Box
        val bg = p().apply { color = android.graphics.Color.parseColor("#F0F0F0"); style = Paint.Style.FILL }
        val stroke = p().apply {
            color = android.graphics.Color.parseColor("#CCCCCC")
            style = Paint.Style.STROKE
            strokeWidth = size * 0.05f
        }

        val rect = RectF(cx - half, cy - half, cx + half, cy + half)
        c.drawRoundRect(rect, 16f, 16f, bg)
        c.drawRoundRect(rect, 16f, 16f, stroke)

        // Lens
        c.drawCircle(cx, cy, size * 0.18f, p().apply { color = android.graphics.Color.parseColor("#999999") })

        // Flash
        val fw = size * 0.28f
        val fh = size * 0.12f
        val fr = RectF(cx - fw / 2f, cy - half + size * 0.06f, cx + fw / 2f, cy - half + size * 0.06f + fh)
        c.drawRoundRect(fr, 8f, 8f, p().apply { color = android.graphics.Color.parseColor("#999999") })
    }


    // ---------------------------------------------------------
    // TEXT UTILITY
    // ---------------------------------------------------------
    private fun ellipsize(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ell = "…"
        var end = text.length
        while (end > 0) {
            val sub = text.substring(0, end) + ell
            if (paint.measureText(sub) <= maxWidth) return sub
            end--
        }
        return ell
    }

    fun isTouchInsideAvatar(touchX: Float, touchY: Float, spec: CardSpecPx, data: CardDataPx): Boolean {

        val baseSize = (data.avatarBitmap?.width?.toFloat() ?: (spec.widthPx.toFloat() * 0.20f))
        val s = data.avatarTransform.scale.coerceIn(0.1f, 8f)

        val cx = spec.widthPx * data.avatarTransform.xNorm
        val cy = spec.heightPx * data.avatarTransform.yNorm

        val radius = (baseSize * s) / 2f

        val dx = touchX - cx
        val dy = touchY - cy

        return dx * dx + dy * dy <= radius * radius
    }

    private fun drawCameraPlaceholderStatic(c: Canvas, spec: CardSpecPx) {

        val radius = spec.widthPx * 0.18f
        val cx = spec.widthPx / 2f
        val cy = spec.heightPx * 0.50f

        // White circle
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        c.drawCircle(cx, cy, radius, paint)

        // Gray border
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#888888")
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.08f
        }
        c.drawCircle(cx, cy, radius, border)

        // Camera icon (simple)
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#666666")
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.12f
        }

        // Lens
        c.drawCircle(cx, cy, radius * 0.35f, iconPaint)

        // Top bar
        val barW = radius * 1.0f
        val barH = radius * 0.35f
        c.drawRoundRect(
            cx - barW / 2f,
            cy - radius * 1.1f,
            cx + barW / 2f,
            cy - radius * 1.1f + barH,
            radius * 0.15f,
            radius * 0.15f,
            iconPaint
        )
    }


}
