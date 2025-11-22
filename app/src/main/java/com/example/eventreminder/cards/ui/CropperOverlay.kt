package com.example.eventreminder.cards.ui

// =============================================================
// Imports
// =============================================================
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import timber.log.Timber
import androidx.core.graphics.scale

// =============================================================
// TAG
// =============================================================
private const val TAG = "CropperOverlay"

// =============================================================
// CropperOverlay — simple zoom/pan crop UI for avatar
// =============================================================
@Composable
fun CropperOverlay(
    sourceBitmap: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val minScale = 0.5f
    val maxScale = 4f
    val viewport = 320.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {

        // ---------------------------
        // Crop viewport
        // ---------------------------
        Box(
            modifier = Modifier
                .size(viewport)
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        offsetX += pan.x
                        offsetY += pan.y
                        scale = (scale * zoom).coerceIn(minScale, maxScale)
                    }
                }
        ) {
            Image(
                bitmap = sourceBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = offsetX
                        translationY = offsetY
                        scaleX = scale
                        scaleY = scale
                    }
            )
        }

        // ---------------------------
        // Buttons
        // ---------------------------
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }

            Button(
                onClick = {
                    try {
                        val scaled = sourceBitmap.scale(
                            (sourceBitmap.width * scale).toInt().coerceAtLeast(1),
                            (sourceBitmap.height * scale).toInt().coerceAtLeast(1)
                        )
                        val size = minOf(scaled.width, scaled.height)
                        val left = (scaled.width - size) / 2
                        val top = (scaled.height - size) / 2
                        val cropped = Bitmap.createBitmap(scaled, left, top, size, size)
                        onConfirm(cropped)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Crop failed")
                    }
                }
            ) {
                Text("Confirm")
            }
        }
    }
}
