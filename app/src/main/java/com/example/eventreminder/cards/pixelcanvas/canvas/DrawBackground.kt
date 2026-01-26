package com.example.eventreminder.cards.pixelcanvas.canvas

import android.graphics.Canvas
import android.graphics.Rect
import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import timber.log.Timber

/**
 * DrawBackground.kt
 *
 * Handles:
 *  1) Background bitmap
 *  2) Safe fallback to gradient
 *  3) Transparent dark overlay tint
 */
object DrawBackground {

    fun drawBackground(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        val bg = data.backgroundBitmap

        if (bg != null) {
            try {
                canvas.drawBitmap(
                    bg,
                    null,
                    Rect(0, 0, spec.widthPx, spec.heightPx),
                    null
                )
            } catch (t: Throwable) {
                Timber.tag("DrawBackground").e(t, "BG failed → fallback gradient")
                DrawGradient.drawGradient(canvas, spec)
            }
        } else {
            DrawGradient.drawGradient(canvas, spec)
        }

        // Subtle overlay tint on top of background
        canvas.drawRect(
            0f, 0f, spec.widthPx.toFloat(), spec.heightPx.toFloat(),
            android.graphics.Paint().apply {
                color = android.graphics.Color.argb(18, 0, 0, 0)
            }
        )
    }
}
