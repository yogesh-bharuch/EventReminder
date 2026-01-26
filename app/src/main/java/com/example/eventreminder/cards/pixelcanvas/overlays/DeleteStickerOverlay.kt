package com.example.eventreminder.cards.pixelcanvas.overlays

// =============================================================
// DeleteStickerOverlay.kt
// - Draws the delete (X) icon above the active sticker
// - Pure UI math: no gesture or VM logic
// - Used inside CardEditorScreen overlay layer
// =============================================================

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import com.example.eventreminder.cards.pixelcanvas.stickers.engine.StickerBitmapCache
import timber.log.Timber

private const val TAG = "DeleteStickerOverlay"

@Composable
fun DeleteStickerOverlay(
    spec: CardSpecPx,
    boxSize: IntSize,
    cardData: CardDataPx,
    onDelete: (Long) -> Unit
) {
    val activeId = cardData.activeStickerId ?: return
    if (boxSize.width == 0 || boxSize.height == 0) return

    val s = cardData.stickers.firstOrNull { it.id == activeId } ?: return
    val density = LocalDensity.current

    // Card → UI scale
    val scale = boxSize.width.toFloat() / spec.widthPx.toFloat()

    // Sticker center in card px
    val cxPx = s.xNorm * spec.widthPx
    val cyPx = s.yNorm * spec.heightPx

    // Base sticker size
    val bmp = when {
        s.drawableResId != null -> StickerBitmapCache.getBitmap(s.drawableResId)
        s.bitmap != null -> s.bitmap
        else -> null
    }

    val baseW = bmp?.width?.toFloat() ?: 120f
    val baseH = bmp?.height?.toFloat() ?: 120f

    // match PixelRenderer scaleBoost = 3.0f
    val finalScale = s.scale * 3.0f

    val stickerWpx = baseW * finalScale
    val stickerHpx = baseH * finalScale

    // Convert sticker center → UI px
    val uiCx = cxPx * scale
    val uiCy = cyPx * scale
    val uiW = stickerWpx * scale
    val uiH = stickerHpx * scale

    // Delete icon (32px) placed at top-right corner
    val iconPx = 32f
    val halfIcon = iconPx / 2f
    val gapPx = 0.2f * density.density

    val iconCenterXpx = uiCx + uiW / 2f - halfIcon
    val iconCenterYpx = uiCy - uiH / 2f - halfIcon - gapPx

    // Debug (optional)
    Timber.tag(TAG).d(
        "DeleteOverlay active=%d uiCx=%.1f uiCy=%.1f icon=(%.1f,%.1f)",
        activeId, uiCx, uiCy, iconCenterXpx, iconCenterYpx
    )

    Popup(
        offset = androidx.compose.ui.unit.IntOffset(
            iconCenterXpx.toInt(),
            iconCenterYpx.toInt()
        ),
        onDismissRequest = {}
    ) {
        Box(Modifier.size(iconPx.dp)) {
            IconButton(
                onClick = {
                    Timber.tag(TAG).d("DELETE CLICK -> id=%d", s.id)
                    onDelete(s.id)
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete sticker",
                    tint = Color.Red,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
