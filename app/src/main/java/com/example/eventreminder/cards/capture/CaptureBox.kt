package com.example.eventreminder.cards.capture

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.core.view.drawToBitmap
import timber.log.Timber

private const val TAG = "CaptureBox"

/**
 * CaptureBox
 *
 * Captures only the composable inside this Box.
 * Tracks exact window position → then crops full screenshot.
 */
@Composable
fun CaptureBox(
    controller: CaptureController,
    modifier: Modifier = Modifier,
    onCaptured: (Bitmap) -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current

    var topLeft: Offset? by remember { mutableStateOf(null) }
    var sizeW by remember { mutableStateOf(0) }
    var sizeH by remember { mutableStateOf(0) }

    Box(
        modifier = modifier
            .graphicsLayer()
            .onGloballyPositioned { layoutCoordinates ->
                // Window offset of this composable (x,y in screen)
                topLeft = layoutCoordinates.localToWindow(Offset.Zero)

                // Capture size in pixels
                sizeW = layoutCoordinates.size.width
                sizeH = layoutCoordinates.size.height
            }
    ) {
        content()
    }

    val request by controller.request.collectAsState()

    LaunchedEffect(request) {
        if (request) {
            try {
                val full = view.drawToBitmap()
                val tl = topLeft

                if (tl != null) {
                    // Crop region of the card only
                    val bmp = Bitmap.createBitmap(
                        full,
                        tl.x.toInt(),
                        tl.y.toInt(),
                        sizeW,
                        sizeH
                    )
                    onCaptured(bmp)

                } else {
                    Timber.tag(TAG).e("Capture failed: topLeft null")
                }

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Capture failed")
            } finally {
                controller.consumed()
            }
        }
    }
}
