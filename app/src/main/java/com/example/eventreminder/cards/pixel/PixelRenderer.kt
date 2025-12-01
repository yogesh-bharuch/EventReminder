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
import com.example.eventreminder.cards.pixel.stickers.StickerBitmapCache
import timber.log.Timber
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "PixelRenderer"

object PixelRenderer {

    // Gradient colors
    private val bgTop = android.graphics.Color.parseColor("#FFFDE7")
    private val bgBottom = android.graphics.Color.parseColor("#FFF0F4")
    var contextProvider: (() -> android.content.Context)? = null

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
     * is the core pixel renderer of your entire card system.
     * is responsible for producing the final exported 1080×1200 PNG that includes:
     * Background, Title, Name, Age circle, Stickers, Dates, Avatar, Shadows, gradients, clipping
     * Main renderer entry.
     * Draw order:
     *   1. Clip to rounded card
     *   2. Background (bitmap or gradient)
     *   3. Subtle overlay
     *   4. Foreground text + stickers
     *   5. FREE-AVATAR (photo moves freely, circular)
     */
    fun renderToAndroidCanvas(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {

        Timber.tag(TAG).d("renderToAndroidCanvas START")

        // ---------------------------------------------------------
        // 1) Clips all drawing inside the card rounded rectangle
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
        // 3) Subtle overlay tint. Adds a soft dark tint over the entire card.
        // improve readability of text, contrast for stickers. professional look
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
        // 4.5) AGE CIRCLE (NEW)
        drawAgeCircle(canvas, spec, data)

        // ---------------------------------------------------------
        // 5) Stickers (NEW FINAL)
        // ---------------------------------------------------------
        renderStickers(canvas, spec, data)   // <-- ONLY ONE CALL HERE

        // ---------------------------------------------------------
        // 6) Date row
        // ---------------------------------------------------------
        drawDateRow(canvas, spec, data)

        // ---------------------------------------------------------
        // 7) FREE AVATAR (after stickers, above everything)
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
    // Title, name, age
    // ---------------------------------------------------------
    private fun drawTitle(c: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.titleBox
        val paint = p().apply {
            color = android.graphics.Color.parseColor("#222222")
            textSize = 72f
            isFakeBoldText = true
        }
        val t = ellipsize(paint, "Happy " + data.titleText, box.width.toFloat())
        c.drawText(t, box.x.toFloat(), box.y + paint.textSize - 12f, paint)
    }

    private fun drawName(c: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.nameBox
        val paint = p().apply {
            color = android.graphics.Color.parseColor("#444444")
            textSize = 72f
        }
        val name = data.nameText
        val t = if (name.isNullOrBlank()) "" else ellipsize(paint, "Dear $name", box.width.toFloat())

        c.drawText(t, box.x.toFloat(), box.y + paint.textSize - 6f, paint)
    }

    private fun drawAgeCircle(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val box = spec.ageBox

        // If value missing → skip
        val age = data.ageOrYearsLabel ?: return
        if (age.isBlank()) return

        // -------------------------------------------
        // 1) Circle background paint
        // -------------------------------------------
        val circlePaint = p().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            setShadowLayer(12f, 0f, 6f, android.graphics.Color.argb(120, 0, 0, 0))
        }

        // -------------------------------------------
        // 2) Draw Gradient age circle
        // -------------------------------------------
        val radius = box.width / 2f

        val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(
                box.x + radius,
                box.y + radius,
                intArrayOf(
                    Color.parseColor("#FFF8B8"),  // light gold
                    Color.parseColor("#E6C200"),  // rich gold
                    Color.parseColor("#D6A100"),  // darker gold
                    Color.parseColor("#FFF8B8")   // smooth loop
                ),
                floatArrayOf(0f, 0.3f, 0.7f, 1f)
            )
            isDither = true
            setShadowLayer(22f, 0f, 10f, Color.argb(160, 0, 0, 0))
        }

        // draw gold circle
        canvas.drawCircle(
            box.x + radius,
            box.y + radius,
            radius,
            sweepPaint
        )

        // -------------------------------------------
        // 3) Age text paint
        // -------------------------------------------
        val textPaint = p().apply {
            color = android.graphics.Color.BLACK
            textSize = (box.height * 0.45f)          // ~45% of circle height
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // vertical centering offset
        val fm = textPaint.fontMetrics
        val textOffset = (fm.descent + fm.ascent) / 2f

        // -------------------------------------------
        // 4) Draw AGE centered in the circle
        // -------------------------------------------
        canvas.drawText(
            age,
            box.x + radius,                     // centerX
            box.y + radius - textOffset,        // centerY correction
            textPaint
        )
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


    // =============================================================
    // Sticker Hit Testing — Step 3
    // =============================================================

    /**
     * Find topmost sticker under the touch point.
     * Iterate in reverse order because last sticker drawn is topmost.
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
     * Matrix-based hit test for a single sticker.
     *
     * Updated to match rendering size exactly (Option A).
     * Uses same scale-boost rules as renderSingleSticker so the
     * touch area equals the visible sticker area.
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

        // Determine the effective rendered width/height (unrotated) to match renderSingleSticker()
        // Two scale phases exist in renderSingleSticker():
        // 1) initial canvas.scale(sticker.scale * 3.5f)
        // 2) drawable-case additional canvas.scale(finalScale) with finalScale = max(sticker.scale * 3.0f, minScale)
        // EffectiveScale (drawn) =
        //    - drawable case: sticker.scale * 3.5f * finalScale
        //    - bitmap/text case: sticker.scale * 3.5f
        //
        // We'll compute half widths/heights from that effective scale.

        // Fetch bitmap if available
        val bmpForSize = when {
            sticker.drawableResId != null -> StickerBitmapCache.getBitmap(sticker.drawableResId!!)
            sticker.bitmap != null -> sticker.bitmap
            else -> null
        }

        val effectiveScale: Float = if (sticker.drawableResId != null && bmpForSize != null) {
            // drawable sticker path: follow same logic used while drawing
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

        // Determine the native width/height to scale
        val nativeW: Float
        val nativeH: Float
        if (bmpForSize != null) {
            nativeW = bmpForSize.width.toFloat()
            nativeH = bmpForSize.height.toFloat()
        } else if (sticker.text != null) {
            // approximate text bounds using StickerPaint utilities (if available)
            val bounds = StickerPaint.getTextBounds(sticker.text!!)
            nativeW = bounds.width().toFloat()
            nativeH = bounds.height().toFloat()
        } else {
            // Fallback to a reasonable base size
            val fallback = spec.widthPx * 0.12f
            nativeW = fallback
            nativeH = fallback
        }

        val halfW = (nativeW * effectiveScale) / 2f
        val halfH = (nativeH * effectiveScale) / 2f

        // Now apply rotation-aware hit test: rotate the touch point into sticker-local coords
        val dx = touchX - cx
        val dy = touchY - cy

        val r = Math.toRadians(sticker.rotationDeg.toDouble())
        val cos = kotlin.math.cos(r)
        val sin = kotlin.math.sin(r)

        val rx = (dx * cos + dy * sin).toFloat()
        val ry = (-dx * sin + dy * cos).toFloat()

        return (rx > -halfW && rx < halfW && ry > -halfH && ry < halfH)
    }

    // =============================================================
//  STICKER RENDERING (STEP-3C — FINAL, RENAMED)
// =============================================================

    private val stickerMatrix = android.graphics.Matrix()

    /**
     * Render all stickers.
     * Bottom → Top order (same order as stored).
     */
    fun renderStickers(
        canvas: android.graphics.Canvas,
        spec: CardSpecPx,
        data: CardDataPx
    ) {
        val list = data.stickers
        if (list.isEmpty()) return

        list.forEach { sticker ->
            renderSingleSticker(canvas, spec, sticker)
        }
    }

    /**
     * Render a single sticker (image resource, bitmap, or emoji text).
     */
    private fun renderSingleSticker(
        canvas: Canvas,
        spec: CardSpecPx,
        sticker: StickerPx
    ) {
        val cx = sticker.xNorm * spec.widthPx
        val cy = sticker.yNorm * spec.heightPx

        canvas.save()

        // Move to sticker center
        canvas.translate(cx, cy)
        canvas.rotate(sticker.rotationDeg)

        // BOOST SIZE → easier for gestures
        val scaleBoost = 3.5f
        canvas.scale(sticker.scale * scaleBoost, sticker.scale * scaleBoost)

        when {
            // -----------------------------------------------------
            // A) DRAWABLE STICKER (WEBP/JPG/PNG) — with min-size boost
            // -----------------------------------------------------
            sticker.drawableResId != null -> {
                val bmp = StickerBitmapCache.getBitmap(sticker.drawableResId!!)
                if (bmp != null) {

                    // 🔥 Make stickers larger + enforce minimum size for visibility
                    val scaleBoost = 3.0f              // increase if still too small
                    val baseScale = sticker.scale * scaleBoost

                    val minSize = 180f                 // px – minimum rendered width
                    val minScale = minSize / bmp.width // if drawable too small

                    val finalScale = max(baseScale, minScale)

                    canvas.scale(finalScale, finalScale)
                    canvas.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, null)
                }
            }

            // -----------------------------------------------------
            // B) Custom bitmap (picked by user)
            // -----------------------------------------------------
            sticker.bitmap != null -> {
                val bmp = sticker.bitmap!!
                val halfW = bmp.width / 2f
                val halfH = bmp.height / 2f
                canvas.drawBitmap(bmp, -halfW, -halfH, null)
            }

            // -----------------------------------------------------
            // C) Emoji text
            // -----------------------------------------------------
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



}
