package com.example.eventreminder.cards.pixelcanvas.canvas

// =============================================================
// DrawGradient.kt
//
// Extracted from PixelRenderer.
// Renders the vertical gradient background when:
//
//   - No backgroundBitmap provided, OR
//   - Bitmap load fails
//
// Absolutely no logic change — same colors, same shader.
// =============================================================

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx

object DrawGradient {

    // Same gradient colors as PixelRenderer
    private val bgTop = android.graphics.Color.parseColor("#FFFDE7")
    private val bgBottom = android.graphics.Color.parseColor("#FFF0F4")

    private fun p() = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Draw fallback vertical gradient fullscreen inside card bounds.
     */
    fun drawGradient(canvas: Canvas, spec: CardSpecPx) {

        val shader = LinearGradient(
            0f, 0f,
            0f, spec.heightPx.toFloat(),
            intArrayOf(bgTop, bgBottom),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.drawRect(
            0f,
            0f,
            spec.widthPx.toFloat(),
            spec.heightPx.toFloat(),
            p().apply { this.shader = shader }
        )
    }
}
