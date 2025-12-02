package com.example.eventreminder.cards.pixelcanvas.canvasui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import com.example.eventreminder.cards.pixelcanvas.PixelCanvas
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
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.LightGray)
            .onGloballyPositioned { coords: LayoutCoordinates ->
                boxSizeState.value = IntSize(
                    coords.size.width,
                    coords.size.height
                )
            }
            .then(gestureModifier)
    ) {
        // Main renderer
        PixelCanvas(
            spec = spec,
            data = cardData,
            modifier = Modifier.fillMaxSize()
        )

        // Delete button overlay
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
