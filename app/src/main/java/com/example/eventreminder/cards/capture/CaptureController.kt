package com.example.eventreminder.cards.capture

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.core.view.drawToBitmap
import timber.log.Timber
import androidx.compose.ui.unit.IntSize

private const val TAG = "CaptureBox"

/**
 * Simple Compose-friendly capture controller.
 *
 * Usage:
 *  val controller = remember { CaptureController() }
 *  CaptureBox(controller = controller, onCaptured = { bmp -> /* handle */ }) { ... }
 *
 * Call controller.capture() to request a capture; CaptureBox will call controller.consumed()
 * after delivering the bitmap.
 */
class CaptureController {
    private val _request = mutableStateOf(false)
    /** Read-only view of the current request state */
    val request: State<Boolean> get() = _request

    /** Request a capture. Compose will observe this and run the capture logic. */
    fun capture() {
        Timber.tag(TAG).d("capture() requested")
        _request.value = true
    }

    /** Mark the current request as handled (called by the CaptureBox). */
    fun consumed() {
        _request.value = false
    }
}

/**
 * CaptureBox composable
 *
 * Wrap any composable that you want to capture as a bitmap. When controller.capture() is called,
 * CaptureBox will:
 *  1. take a full-window bitmap using LocalView.drawToBitmap()
 *  2. crop the rectangle that corresponds to the wrapped composable (measured with onGloballyPositioned)
 *  3. call onCaptured(croppedBitmap)
 *
 * Notes:
 * - drawToBitmap requires the view to be attached; this code assumes the composable is visible.
 * - cropping clamps to the full bitmap bounds to avoid IllegalArgumentException on some devices.
 */
@Composable
fun CaptureBox(
    controller: CaptureController,
    modifier: Modifier = Modifier,
    onCaptured: (Bitmap) -> Unit,
    content: @Composable () -> Unit
) {
    val view = LocalView.current

    // Track composable's window position and size (in pixels)
    var topLeft: Offset? by remember { mutableStateOf(null) }
    var sizePx: IntSize by remember { mutableStateOf(IntSize(0, 0)) }

    Box(
        modifier = modifier.onGloballyPositioned { layoutCoordinates ->
            // localToWindow gives top-left position in window coordinates (pixels)
            topLeft = layoutCoordinates.localToWindow(Offset.Zero)
            sizePx = IntSize(layoutCoordinates.size.width, layoutCoordinates.size.height)
        }
    ) {
        // Render the content normally
        content()
    }

    // read request as plain boolean (State<Boolean>) and observe changes via LaunchedEffect key
    val request = controller.request.value

    LaunchedEffect(request) {
        if (!request) return@LaunchedEffect

        try {
            Timber.tag(TAG).d("Capture requested -> taking full window screenshot")
            val fullBitmap = view.drawToBitmap() // full window bitmap

            val tl = topLeft
            val w = sizePx.width
            val h = sizePx.height

            if (tl == null) {
                Timber.tag(TAG).e("Capture failed: composable top-left is null")
            } else if (w <= 0 || h <= 0) {
                Timber.tag(TAG).e("Capture failed: invalid composable size w=$w h=$h")
            } else {
                // Compute crop rect, clamp to full bitmap bounds to avoid exceptions
                val startX = tl.x.toInt().coerceIn(0, fullBitmap.width - 1)
                val startY = tl.y.toInt().coerceIn(0, fullBitmap.height - 1)

                // Make sure width/height don't overflow
                val cropW = if (startX + w > fullBitmap.width) fullBitmap.width - startX else w
                val cropH = if (startY + h > fullBitmap.height) fullBitmap.height - startY else h

                if (cropW <= 0 || cropH <= 0) {
                    Timber.tag(TAG).e("Capture failed: computed crop size invalid cropW=$cropW cropH=$cropH full=${fullBitmap.width}x${fullBitmap.height} startX=$startX startY=$startY")
                } else {
                    val cropped = Bitmap.createBitmap(fullBitmap, startX, startY, cropW, cropH)
                    Timber.tag(TAG).d("Captured crop %dx%d at (%d,%d)", cropW, cropH, startX, startY)
                    onCaptured(cropped)
                }
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Capture failed")
        } finally {
            // mark the request handled so controller can be reused
            controller.consumed()
        }
    }
}
