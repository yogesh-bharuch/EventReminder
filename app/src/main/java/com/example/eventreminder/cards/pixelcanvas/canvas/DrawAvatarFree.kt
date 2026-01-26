package com.example.eventreminder.cards.pixelcanvas.canvas

// =============================================================
// DrawAvatarFree.kt
// Renders the free-transform avatar layer:
// - Circular masked bitmap
// - Or placeholder if no photo
// - Uses Matrix for translation, scale, rotation
// =============================================================

import android.graphics.*
import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import timber.log.Timber
import kotlin.math.min

private const val TAG = "DrawAvatarFree"

object DrawAvatarFree {

    /**
     * Draws the user avatar:
     * - If bitmap present → circular masked, movable, zoomable avatar.
     * - If null → static camera placeholder.
     */
    fun drawAvatarFree(c: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val bmp = data.avatarBitmap

        // Compute avatar center & transform
        val cx = spec.widthPx * data.avatarTransform.xNorm
        val cy = spec.heightPx * data.avatarTransform.yNorm
        val s = data.avatarTransform.scale.coerceIn(0.1f, 8f)
        val rot = data.avatarTransform.rotationDeg

        // Default avatar size base
        val baseSize = bmp?.width ?: (spec.widthPx * 0.20f).toInt()
        val avatarSize = baseSize * s
        val radius = avatarSize / 2f

        // -------------------------------------------------------
        // CASE A — NO PHOTO → STATIC CAMERA PLACEHOLDER
        // -------------------------------------------------------
        if (bmp == null) {
            drawCameraPlaceholderStatic(c, spec)
            return
        }

        try {
            // -------------------------------------------------------
            // CASE B — REAL PHOTO → circular masked free-moving image
            // -------------------------------------------------------
            val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

            val mat = Matrix().apply {
                // 1) Move bitmap origin to center
                postTranslate(-bmp.width / 2f, -bmp.height / 2f)

                // 2) Scale
                postScale(s, s)

                // 3) Rotate
                postRotate(rot)

                // 4) Move into card coordinate system
                postTranslate(cx, cy)
            }

            shader.setLocalMatrix(mat)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.shader = shader
            }

            c.save()
            c.drawCircle(cx, cy, radius, paint)
            c.restore()

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "drawAvatarFree failed")
        }
    }


    // -------------------------------------------------------------------
    // STATIC CAMERA PLACEHOLDER (drawn when avatarBitmap == null)
    // -------------------------------------------------------------------
    private fun drawCameraPlaceholderStatic(c: Canvas, spec: CardSpecPx) {

        val radius = spec.widthPx * 0.18f
        val cx = spec.widthPx / 2f
        val cy = spec.heightPx * 0.50f

        // Background circle
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        c.drawCircle(cx, cy, radius, bg)

        // Rim
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#888888")
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.08f
        }
        c.drawCircle(cx, cy, radius, rim)

        // Lens
        val lensPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#666666")
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.12f
        }
        c.drawCircle(cx, cy, radius * 0.35f, lensPaint)

        // Flash block
        val flashW = radius * 1.0f
        val flashH = radius * 0.35f
        val flashRect = RectF(
            cx - flashW / 2f,
            cy - radius * 1.1f,
            cx + flashW / 2f,
            cy - radius * 1.1f + flashH
        )
        c.drawRoundRect(
            flashRect,
            radius * 0.15f,
            radius * 0.15f,
            lensPaint
        )
    }
}
