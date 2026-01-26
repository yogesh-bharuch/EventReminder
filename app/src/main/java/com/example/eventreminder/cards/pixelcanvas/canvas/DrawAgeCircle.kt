package com.example.eventreminder.cards.pixelcanvas.canvas

// =============================================================
// DrawAgeCircle.kt
// Renders the gold gradient age/years circle inside spec.ageBox.
// Moved out of PixelRenderer for cleaner structure.
// =============================================================

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SweepGradient
import android.graphics.Typeface
import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import timber.log.Timber

private const val TAG = "DrawAgeCircle"

/**
 * Draws the gold circular "age / anniversary years" badge.
 *
 * Behavior:
 * - If data.ageOrYearsLabel is null/blank → skips drawing.
 * - Gold sweep gradient circle.
 * - Bold big number centered in circle.
 * - Shadow for a premium look.
 */
object DrawAgeCircle {

    fun drawAgeCircle(canvas: Canvas, spec: CardSpecPx, data: CardDataPx) {
        try {
            val box = spec.ageBox
            val age = data.ageOrYearsLabel ?: return
            if (age.isBlank()) return

            val radius = box.width / 2f

            // -----------------------------------------------------------------
            // 1) GOLD GRADIENT CIRCLE (SWEEP)
            // -----------------------------------------------------------------
            val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = SweepGradient(
                    box.x + radius,
                    box.y + radius,
                    intArrayOf(
                        Color.parseColor("#FFF8B8"),   // light gold
                        Color.parseColor("#E6C200"),   // rich gold
                        Color.parseColor("#D6A100"),   // deep gold
                        Color.parseColor("#FFF8B8")    // smooth loop
                    ),
                    floatArrayOf(0f, 0.3f, 0.7f, 1f)
                )
                isDither = true
                setShadowLayer(22f, 0f, 10f, Color.argb(160, 0, 0, 0))
            }

            canvas.drawCircle(
                box.x + radius,
                box.y + radius,
                radius,
                sweepPaint
            )

            // -----------------------------------------------------------------
            // 2) AGE NUMBER TEXT
            // -----------------------------------------------------------------
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = box.height * 0.45f      // 45% of circle height
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            // Vertical centering trick
            val fm = textPaint.fontMetrics
            val textOffset = (fm.descent + fm.ascent) / 2f

            canvas.drawText(
                age,
                box.x + radius,
                box.y + radius - textOffset,
                textPaint
            )

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to draw age circle")
        }
    }
}
