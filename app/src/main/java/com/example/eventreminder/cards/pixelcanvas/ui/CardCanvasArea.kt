package com.example.eventreminder.cards.pixelcanvas.ui

// =============================================================
// CardCanvasArea
// - Wraps PixelCanvas inside a Box
// - Applies gesture modifiers
// - Computes boxSize
// - Shows DeleteStickerButtonOverlay
// =============================================================

//import androidx.compose.foundation.layout.onGloballyPositioned
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import com.example.eventreminder.cards.pixelcanvas.overlays.DeleteStickerOverlay

@Composable
fun CardCanvasArea(
    spec: CardSpecPx,
    cardData: CardDataPx,
    boxSizeState: MutableState<IntSize>,
    gestureModifier: Modifier,
    onDeleteSticker: (Long) -> Unit
) {
    val boxSize = boxSizeState.value

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .aspectRatio(1080f / 1200f)        // <-- RESTORED: Proper canvas space
            .background(Color.LightGray)
            .onGloballyPositioned { coords ->
                boxSizeState.value = IntSize(
                    coords.size.width,
                    coords.size.height
                )
            }
            .then(gestureModifier)             // gesture recognizer ON TOP
    ) {

        // ---------------------------
        // Main Pixel Canvas → PixelRenderer.draw(canvas, spec, cardData)
        // ---------------------------
        PixelCanvas(
            spec = spec,
            data = cardData,
            modifier = Modifier.fillMaxSize()
        )

        // ---------------------------
        // Delete Button Overlay (popup)
        // ---------------------------
        if (cardData.activeStickerId != null) {
            DeleteStickerOverlay(
                spec = spec,
                boxSize = boxSize,
                cardData = cardData,
                onDelete = onDeleteSticker
            )
        }
    }
}
