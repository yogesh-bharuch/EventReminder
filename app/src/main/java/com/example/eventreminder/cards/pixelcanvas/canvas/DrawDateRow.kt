package com.example.eventreminder.cards.pixelcanvas.canvas

// =============================================================
// DrawDateRow.kt
// Renders the bottom date row: original date (left) + next date (right).
// Extracted cleanly from PixelRenderer.
// =============================================================

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import timber.log.Timber

private const val TAG = "DrawDateRow"

object DrawDateRow {

    /**
     * Draws the date row line inside spec.dateBox.
     * Left 60% → original date (e.g., "Nov 26, 1987")
     * Right 40% → next date (e.g., "Wed, Nov 26, 2025")
     */
    fun drawDateRow(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        try {
            val box = spec.dateBox

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = data.originalDateColor //Color.parseColor("#666666")
                textSize = 40f
                isFakeBoldText = true
            }

            val y = box.y + paint.textSize

            // Left (60%)
            val left = PixelTextUtils.ellipsize(paint, data.originalDateLabel, box.width * 0.6f)
            canvas.drawText(left, box.x.toFloat(), y, paint)

            // Right (40%)
            //val right = PixelTextUtils.ellipsize(paint, data.nextDateLabel, box.width * 0.4f)
            val right = PixelTextUtils.ellipsize(paint, "Yogesh", box.width * 0.4f)
            val rightWidth = paint.measureText(right)

            canvas.drawText(
                right,
                (box.x + box.width - rightWidth).toFloat(),
                y,
                paint
            )

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to draw date row")
        }
    }

}
