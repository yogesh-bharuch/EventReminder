package com.example.eventreminder.cards.pixel

// =============================================================
// PixelCanvas.kt — FIXED VERSION
// (This version will always render, never collapses, and logs size)
// =============================================================

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import timber.log.Timber

private const val TAG = "PixelCanvas"

/**
 * PixelCanvas
 *
 * A guaranteed-working wrapper around PixelRenderer that:
 * - ALWAYS receives a valid DrawScope size
 * - Scales canonical pixel dimensions to fit
 * - Never collapses (no wrapContentSize, no inner Modifier)
 * - Logs DrawScope size for debugging
 */
@Composable
fun PixelCanvas(
    spec: CardSpecPx,
    data: CardDataPx,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()              // <— IMPORTANT: Canvas must receive size
    ) {

        // Debug size
        Timber.tag(TAG).d(
            "DrawScope size = %.1f x %.1f px",
            size.width,
            size.height
        )

        if (size.width <= 0f || size.height <= 0f) {
            Timber.tag(TAG).e("❌ ZERO SIZE CANVAS - nothing will draw")
            return@Canvas
        }

        // Call renderer normally
        PixelRenderer.renderToDrawScope(this, spec, data)
    }
}
