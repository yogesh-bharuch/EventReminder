package com.example.eventreminder.cards.pixelcanvas.canvas

// =============================================================
// DrawTitle.kt
// - Responsible for drawing the card title (top area)
// - Self-contained ellipsize helper to avoid breaking renderer while refactoring
// - Follow project standards: Timber TAG, full imports, comments
// =============================================================

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import timber.log.Timber

private const val TAG = "DrawTitle"

// -------------------------------------------------------------
// Public entry: drawTitle
// - Draws the card title inside spec.titleBox
// - Uses a bold, large paint and ellipsizes to fit width
// -------------------------------------------------------------
object DrawTitle {

    /**
     * Draw the title text inside the titleBox of the provided spec.
     *
     * @param c Canvas where drawing occurs (card pixel coordinates)
     * @param spec CardSpecPx describing layout rectangles and dimensions
     * @param data CardDataPx containing titleText and other info
     */
    fun drawTitle(c: Canvas, spec: CardSpecPx, data: CardDataPx) {
        try {
            val box = spec.titleBox

            // Configure paint for title — bold, large
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#222222")
                textSize = 72f
                isFakeBoldText = true
            }

            // Compose the title string and ellipsize to available width
            // Note: use safe concatenation — if titleText is blank, we still draw "Happy " only.
            val raw = "Happy " + (data.titleText ?: "")
            val t = PixelTextUtils.ellipsize(paint, raw, box.width.toFloat())

            // Draw text at box.x, baseline tuned by paint.textSize
            c.drawText(t, box.x.toFloat(), box.y + paint.textSize - 12f, paint)

        } catch (t: Throwable) {
            Timber.tag(TAG).w(t, "drawTitle failed")
        }
    }

}
