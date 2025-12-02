package com.example.eventreminder.cards.pixelcanvas

// =============================================================
// PixelCanvas.kt — stable wrapper around PixelRenderer
// - Delegates drawing to PixelRenderer.renderToDrawScope()
// - Ensures Compose invalidates when data changes
// - Logs current canvas size for debugging
// - No delete icon or overlays here (handled by UI layer)
// =============================================================

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.eventreminder.cards.pixelcanvas.canvas.PixelRenderer
import timber.log.Timber

private const val TAG = "PixelCanvas"

@Composable
fun PixelCanvas(
    spec: CardSpecPx,
    data: CardDataPx,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val w = size.width
        val h = size.height

        Timber.tag(TAG).d("🎨 PixelCanvas draw size = ${w} × ${h}")

        if (w <= 0f || h <= 0f) {
            Timber.tag(TAG).e("❌ PixelCanvas: ZERO SIZE — skipping draw")
            return@Canvas
        }

        // The correct new pipeline entry:
        // PixelRenderer handles scaling, clipping, export logic internally.
        PixelRenderer.renderToDrawScope(
            ds = this,
            spec = spec,
            data = data
        )
    }
}
