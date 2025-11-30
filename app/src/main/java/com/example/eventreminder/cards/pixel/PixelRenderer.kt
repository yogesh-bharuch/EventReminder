package com.example.eventreminder.cards.pixel

// =============================================================
// PixelRenderer.kt  (UPDATED)
// - safer avatar handling: clamps, scale limits, robust bitmap scaling
// - kept draw order: clip -> background -> overlay -> foreground (title/name/stickers/dates/avatar inside clip)
// - debug logging included
// =============================================================

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import timber.log.Timber
import kotlin.math.roundToInt

private const val TAG = "PixelRenderer"

object PixelRenderer {

    // Background gradient colors (android.graphics.Color is used via parseColor)
    private val defaultBgTopColor = android.graphics.Color.parseColor("#FFFDE7")
    private val defaultBgBottomColor = android.graphics.Color.parseColor("#FFF0F4")

    private fun makePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---------------------------------------------------------
    //  ANDROID CANVAS RENDERER
    // ---------------------------------------------------------
    fun renderToAndroidCanvas(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        Timber.tag("DRAWFLOW").d("renderToAndroidCanvas CALLED")

        // 1) Clip card shape
        canvas.save()
        val rectF = spec.backgroundLayer.toRectF()
        val path = Path().apply {
            addRoundRect(
                rectF,
                spec.cornerRadiusPx.toFloat(),
                spec.cornerRadiusPx.toFloat(),
                Path.Direction.CW
            )
        }
        canvas.clipPath(path)

        // 2) Background: bitmap -> fallback gradient
        if (data.backgroundBitmap != null) {
            try {
                val dest = Rect(0, 0, spec.widthPx, spec.heightPx)
                canvas.drawBitmap(data.backgroundBitmap!!, null, dest, makePaint())
            } catch (t: Throwable) {
                Timber.tag(TAG).w(t, "BG draw failed -> fallback gradient")
                drawGradientBackground(canvas, spec)
            }
        } else {
            drawGradientBackground(canvas, spec)
        }

        // subtle overlay for uniform look
        val overlay = makePaint().apply {
            color = android.graphics.Color.argb(18, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, spec.widthPx.toFloat(), spec.heightPx.toFloat(), overlay)

        // 3) Draw foreground inside clip
        drawTitle(canvas, spec, data)
        drawName(canvas, spec, data)
        drawStickers(canvas, spec, data)
        drawDateRow(canvas, spec, data)

        // 4) Avatar drawn inside clip (will be clamped to visible area)
        data.avatarBitmap?.let { drawAvatar(canvas, spec, data) }

        canvas.restore()
    }

    private fun drawGradientBackground(canvas: Canvas, spec: CardSpecPx) {
        val shader = LinearGradient(
            0f, 0f, 0f, spec.heightPx.toFloat(),
            intArrayOf(defaultBgTopColor, defaultBgBottomColor),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        val p = makePaint().apply { this.shader = shader; style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, spec.widthPx.toFloat(), spec.heightPx.toFloat(), p)
    }

    // ---------------------------------------------------------
    // COMPOSE ENTRY
    // ---------------------------------------------------------
    fun renderToDrawScope(drawScope: DrawScope, spec: CardSpecPx, data: CardDataPx) {
        val native = drawScope.drawContext.canvas.nativeCanvas
        val scaleX = drawScope.size.width / spec.widthPx.toFloat()
        val scaleY = drawScope.size.height / spec.heightPx.toFloat()
        val scale = minOf(scaleX, scaleY)

        native.save()
        native.scale(scale, scale)
        try {
            renderToAndroidCanvas(native, spec, data)
        } finally {
            native.restore()
        }
    }

    // ---------------------------------------------------------
    // FOREGROUND: TITLE / NAME
    // ---------------------------------------------------------
    private fun drawTitle(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.titleBox
        val p = makePaint().apply {
            color = android.graphics.Color.parseColor("#222222")
            textSize = 72f
            isFakeBoldText = true
        }
        val txt = ellipsize(p, data.titleText, box.width.toFloat())
        val x = box.x.toFloat()
        val y = box.y + p.textSize - 12f
        canvas.drawText(txt, x, y, p)
    }

    private fun drawName(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.nameBox
        val p = makePaint().apply {
            color = android.graphics.Color.parseColor("#444444")
            textSize = 48f
        }
        val txt = ellipsize(p, data.nameText ?: "", box.width.toFloat())
        val x = box.x.toFloat()
        val y = box.y + p.textSize - 6f
        canvas.drawText(txt, x, y, p)
    }

    // ---------------------------------------------------------
    // AVATAR DRAWER (safe, clamped, supports scale & rotation)
    // ---------------------------------------------------------
    private fun drawAvatar(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {

        val bmp = data.avatarBitmap ?: run {
            Timber.tag("AVATAR_DRAW").e("drawAvatar: bitmap NULL")
            return
        }

        // ---- 1) Sanitize transform values ----
        val xNorm = data.avatarTransform.xNorm.coerceIn(0f, 1f)
        val yNorm = data.avatarTransform.yNorm.coerceIn(0f, 1f)
        val scaleNorm = data.avatarTransform.scale.coerceIn(0.3f, 6f)
        val rotationDeg = data.avatarTransform.rotationDeg

        // ---- 2) Compute center px ----
        // No clamping here — clamp AFTER computing radius
        val cxRaw = (spec.widthPx * xNorm)
        val cyRaw = (spec.heightPx * yNorm)

        // ---- 3) Compute avatar final size ----
        // 0.22f = best balanced size (not huge, not tiny)
        val baseSize = (spec.widthPx * 0.45f).toInt()
        val finalSize = (baseSize * scaleNorm).toInt().coerceAtLeast(24)
        val radius = finalSize / 2f

        // ---- 4) Clamp center to keep avatar visible ----
        /*val cx = cxRaw.roundToInt().coerceIn(radius.toInt(), spec.widthPx - radius.toInt())
        val cy = cyRaw.roundToInt().coerceIn(radius.toInt(), spec.heightPx - radius.toInt())*/
        val cx = cxRaw.roundToInt()
        val cy = cyRaw.roundToInt()

        Timber.tag("AVATAR_DRAW").d(
            "drawAvatar: xNorm=%.3f yNorm=%.3f  -> cx=%d cy=%d  size=%d  scale=%.3f rot=%.1f",
            xNorm, yNorm, cx, cy, finalSize, scaleNorm, rotationDeg
        )


        // ---- 5) Safely scale bitmap ----
        val scaled = try {
            Bitmap.createScaledBitmap(bmp, finalSize, finalSize, true)
        } catch (t: Throwable) {
            Timber.tag("AVATAR_DRAW").w(t, "Scaling failed — using original bmp")
            bmp
        }
        Timber.tag("AVATAR_DRAW").d("scaled placeholder = ${scaled.width} x ${scaled.height}, finalSize=$finalSize")

        canvas.save()

        // ---- 6) Apply rotation around the avatar center ----
        if (rotationDeg != 0f) {
            canvas.rotate(rotationDeg, cx.toFloat(), cy.toFloat())
        }

        // ---- 7) Circular mask shader ----
        val shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.shader = shader
            isFilterBitmap = true
        }

        // ---- 8) Draw avatar circle ----
        canvas.drawCircle(cx.toFloat(), cy.toFloat(), radius, paint)

        canvas.restore()

        Timber.tag("AVATAR_DRAW").d("drawAvatar DONE at ($cx,$cy) radius=%.1f", radius)
    }

    // ---------------------------------------------------------
    // STICKERS
    // ---------------------------------------------------------
    private fun drawStickers(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        data.stickers.forEach { s ->
            val cx = (s.xNorm * spec.widthPx).roundToInt()
            val cy = (s.yNorm * spec.heightPx).roundToInt()

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
                    color = android.graphics.Color.parseColor("#222222")
                    textSize = 28f
                }
                val txt = s.text ?: "◻"
                canvas.drawText(txt, -half + 8f, 8f, p)
            }
            canvas.restore()
        }
    }

    // ---------------------------------------------------------
    // DATE ROW
    // ---------------------------------------------------------
    private fun drawDateRow(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.dateBox
        val p = makePaint().apply {
            color = android.graphics.Color.parseColor("#666666")
            textSize = 40f
        }
        val y = box.y + p.textSize
        val leftText = ellipsize(p, data.originalDateLabel, box.width * 0.6f)
        canvas.drawText(leftText, box.x.toFloat(), y, p)

        val rightText = ellipsize(p, data.nextDateLabel, box.width * 0.4f)
        val wRight = p.measureText(rightText)
        canvas.drawText(rightText, (box.x + box.width - wRight).toFloat(), y, p)
    }

    // ---------------------------------------------------------
    // TEXT UTILITY
    // ---------------------------------------------------------
    private fun ellipsize(p: Paint, text: String, maxWidth: Float): String {
        if (p.measureText(text) <= maxWidth) return text
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
