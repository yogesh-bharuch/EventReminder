package com.example.eventreminder.card_o.render

// =============================================================
// Imports
// =============================================================
import android.graphics.Canvas
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import timber.log.Timber
import com.example.eventreminder.card_o.model.TextAlignment

// =============================================================
// Constants
// =============================================================
private const val TAG = "TextLayoutEngine"

/**
 * TextLayoutEngine
 *
 * Responsible for drawing wrapped, aligned, auto-fitted text
 * onto a Canvas with correct X/Y positioning.
 *
 * IMPORTANT:
 * - Uses StaticLayout (the correct Android text layout engine).
 * - We must *translate canvas* before drawing the layout.
 * - We do NOT draw text manually after StaticLayout.
 */
object TextLayoutEngine {

    // =============================================================
    // Public API
    // =============================================================

    /**
     * Draw a text block safely with wrapping + alignment + optional autoFit.
     *
     * @param canvas Canvas to draw into
     * @param text String text to draw
     * @param x Left/center/right anchor X (depends on alignment)
     * @param y Top offset (not baseline)
     * @param paint Base paint (converted to TextPaint internally)
     * @param maxWidth Max allowed width (if null → no wrap)
     * @param alignment Left, Center, Right
     * @param autoFit Reduce text size until text fits maxWidth
     */
    fun drawTextBlock(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: Paint,
        maxWidth: Float?,
        alignment: TextAlignment,
        autoFit: Boolean
    ) {
        Timber.tag(TAG).d(
            "drawTextBlock: \"$text\"  x=$x  y=$y  maxWidth=$maxWidth  align=$alignment  autoFit=$autoFit"
        )

        // ---------------------------------------------------------
        // Convert to TextPaint (StaticLayout requires TextPaint)
        // ---------------------------------------------------------
        val tp = TextPaint(paint)

        // ---------------------------------------------------------
        // Auto-fit logic (reduce until fits)
        // ---------------------------------------------------------
        if (autoFit && maxWidth != null) {
            val minSize = 24f   // avoid unreadable tiny fonts
            while (tp.measureText(text) > maxWidth && tp.textSize > minSize) {
                tp.textSize -= 2f
            }
        }


        // ---------------------------------------------------------
        // Determine layout width
        // ---------------------------------------------------------
        val layoutWidth = maxWidth?.toInt() ?: tp.measureText(text).toInt()

        // ---------------------------------------------------------
        // Convert enum → StaticLayout alignment
        // ---------------------------------------------------------
        val layoutAlign = when (alignment) {
            TextAlignment.Left -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.Center -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.Right -> Layout.Alignment.ALIGN_OPPOSITE
        }

        // ---------------------------------------------------------
        // Build StaticLayout (Android's real text engine)
        // ---------------------------------------------------------
        val staticLayout = StaticLayout.Builder
            .obtain(text, 0, text.length, tp, layoutWidth)
            .setAlignment(layoutAlign)
            .setIncludePad(false)
            .setLineSpacing(0f, 1.2f)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setMaxLines(Int.MAX_VALUE)
            .build()

        // ---------------------------------------------------------
        // Translate canvas (X/Y)
        // AND THEN draw layout
        // ---------------------------------------------------------
        canvas.save()

        // Center alignment requires shifting starting X
        val translatedX = when (alignment) {
            TextAlignment.Left -> x
            TextAlignment.Center -> x - (layoutWidth / 2f)
            TextAlignment.Right -> x - layoutWidth.toFloat()
        }

        canvas.translate(translatedX, y)
        staticLayout.draw(canvas)

        canvas.restore()
    }
}
