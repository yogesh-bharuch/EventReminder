package com.example.eventreminder.cards.pixelcanvas.canvas

// =============================================================
// PixelTextUtils.kt
// Small utility functions for PixelRenderer text handling.
// Extracted from PixelRenderer to keep renderer focused on drawing.
// =============================================================

import android.graphics.Paint
import timber.log.Timber

private const val TAG = "PixelTextUtils"

object PixelTextUtils {

    /**
     * Ellipsize text to fit inside maxWidth using the provided Paint.
     * Behavior preserved exactly from original PixelRenderer.returns
     * the original string if it already fits.
     */
    fun ellipsize(paint: Paint, text: String, maxWidth: Float): String {
        return try {
            if (paint.measureText(text) <= maxWidth) return text
            val ell = "…"
            var end = text.length
            while (end > 0) {
                val sub = text.substring(0, end) + ell
                if (paint.measureText(sub) <= maxWidth) return sub
                end--
            }
            ell
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "ellipsize failed")
            // Fallback: return as-is (caller will handle rendering)
            text
        }
    }
}
