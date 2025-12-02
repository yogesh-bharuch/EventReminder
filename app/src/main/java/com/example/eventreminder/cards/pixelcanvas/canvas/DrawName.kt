package com.example.eventreminder.cards.pixelcanvas.canvas

// =============================================================
// DrawName.kt
// Renders the "Dear <Name>" text inside the nameBox.
// Separated cleanly from PixelRenderer for readability.
// =============================================================

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import timber.log.Timber

private const val TAG = "DrawName"

/**
 * Draws the name row, e.g. "Dear Rahul".
 *
 * Behavior:
 * - If data.nameText is null/blank → draws nothing.
 * - Auto-ellipsizes to stay inside box width.
 * - Uses same font, size, and offsets as the old PixelRenderer.
 */
object DrawName {

    /**
     * Public API
     */
    fun drawName(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        try {
            val name = data.nameText
            if (name.isNullOrBlank()) return     // ← skip if no name

            val box = spec.nameBox
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#444444")
                textSize = 72f
            }

            val text = PixelTextUtils.ellipsize(
                paint = paint,
                text = "Dear $name",
                maxWidth = box.width.toFloat()
            )

            // Draw baseline-aligned text
            canvas.drawText(
                text,
                box.x.toFloat(),
                box.y + paint.textSize - 6f,
                paint
            )

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to draw name")
        }
    }


}
