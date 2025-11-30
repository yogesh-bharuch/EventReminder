package com.example.eventreminder.cards.pixel

// =============================================================
// PixelCanvas.kt — stable minimal wrapper for PixelRenderer
// - Uses drawIntoCanvas to ensure the Canvas redraws when data
//   (CardDataPx) changes in Compose state.
// - Always fills available space so DrawScope.size is valid.
// - Logs draw size for debugging.
// =============================================================

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import timber.log.Timber

private const val TAG = "PixelCanvas"

@Composable
fun PixelCanvas(
    spec: CardSpecPx,
    data: CardDataPx,
    modifier: Modifier = Modifier
) {
    // Canvas must receive size from parent (fillMaxSize recommended).
    Canvas(
        modifier = modifier
            .fillMaxSize()
    ) {
        val w = size.width
        val h = size.height

        Timber.tag(TAG).d("Canvas draw() size = %.1f × %.1f".format(w, h))

        if (w <= 0f || h <= 0f) {
            Timber.tag(TAG).e("❌ ZERO SIZE CANVAS — skip draw")
            return@Canvas
        }

        // drawIntoCanvas ensures the native canvas draw gets invoked on Compose redraws
        // which happen when 'data' (or any remembered state used by the caller) changes.
        drawIntoCanvas { canvas ->
            try {
                PixelRenderer.renderToAndroidCanvas(canvas.nativeCanvas, spec, data)
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "PixelRenderer.renderToAndroidCanvas failed")
            }
        }
    }
}
