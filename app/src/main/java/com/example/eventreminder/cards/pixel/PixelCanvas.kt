package com.example.eventreminder.cards.pixel

// =============================================================
// PixelCanvas.kt
// Compose wrapper composable that displays a canonical pixel card
// scaled to available space while keeping 1:1 pixel rendering logic.
// =============================================================

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import timber.log.Timber

private const val TAG = "PixelCanvas"

/**
 * PixelCanvas Composable
 *
 * - Renders the given CardDataPx according to the canonical CardSpecPx.
 * - The canvas will scale the canonical pixels to fit the available Compose size,
 *   preserving aspect ratio.
 *
 * Usage:
 *  val spec = CardSpecPx.default1080x720()
 *  PixelCanvas(spec = spec, data = dataPx, modifier = Modifier.width(...).height(...))
 */
@Composable
fun PixelCanvas(
    spec: CardSpecPx,
    data: CardDataPx,
    modifier: Modifier = Modifier
) {
    // Maintain aspect ratio of canonical spec
    val aspect = spec.widthPx.toFloat() / spec.heightPx.toFloat()

    Box(
        modifier = modifier
            .aspectRatio(aspect)
            .wrapContentSize()
    ) {
        Canvas(modifier = Modifier) {
            // The DrawScope provided here will be scaled internally by PixelRenderer
            // so we can simply call the renderer entrypoint.
            PixelRenderer.renderToDrawScope(this, spec, data)
        }
    }
}
