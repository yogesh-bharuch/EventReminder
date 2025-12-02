package com.example.eventreminder.cards.pixelcanvas.editor

// =============================================================
// GestureHandlers.kt — Extracted gesture logic for CardEditorScreen
// - Handles sticker drag / rotate / scale
// - Handles avatar drag / rotate / scale
// - Handles sticker selection + clearing
// - Pure logic, NO UI elements
// =============================================================

import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.example.eventreminder.cards.CardViewModel
import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import com.example.eventreminder.cards.pixelcanvas.canvas.TouchAvatarUtils
import com.example.eventreminder.cards.pixelcanvas.canvas.TouchStickerUtils
//import com.example.eventreminder.cards.pixelcanvas.StickerPx
import com.example.eventreminder.cards.pixelcanvas.stickers.StickerPx


object GestureHandlers {

    fun modifier(
        spec: CardSpecPx,
        boxSize: IntSize,
        cardData: CardDataPx,
        stickerList: List<StickerPx>,
        activeStickerId: Long?,
        viewModel: CardViewModel
    ): Modifier {
        return Modifier.pointerInput(spec, boxSize, stickerList, activeStickerId) {

            awaitPointerEventScope {

                while (true) {

                    var event = awaitPointerEvent()
                    val first = event.changes.firstOrNull { it.pressed } ?: continue

                    val tx = first.position.x
                    val ty = first.position.y

                    var touchedStickerOnDown = false

                    // ------------------------------------------------------
                    // 1) STICKERS (topmost)
                    // ------------------------------------------------------
                    val touchedSticker = TouchStickerUtils.findTopmostStickerUnderTouch(
                        touchX = tx,
                        touchY = ty,
                        spec = spec,
                        data = cardData
                    )

                    if (touchedSticker != null) {
                        touchedStickerOnDown = true
                        viewModel.setActiveSticker(touchedSticker.id)

                        // Sticker gesture loop
                        while (event.changes.any { it.pressed }) {

                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom().coerceFinite()
                            val rot = event.calculateRotation().coerceFinite()

                            viewModel.updateActiveStickerPosition(
                                dxNorm = pan.x / boxSize.width.toFloat(),
                                dyNorm = pan.y / boxSize.height.toFloat()
                            )

                            viewModel.updateActiveStickerScale(zoom)
                            viewModel.updateActiveStickerRotation(rot)

                            event.changes.forEach { it.consume() }
                            event = awaitPointerEvent()
                        }
                        continue
                    }

                    // ------------------------------------------------------
                    // 2) AVATAR
                    // ------------------------------------------------------
                    val hitAvatar = TouchAvatarUtils.isTouchInsideAvatar(
                        touchX = tx,
                        touchY = ty,
                        spec = spec,
                        data = cardData
                    )

                    if (hitAvatar) {

                        viewModel.setActiveSticker(null)

                        // Avatar gesture loop
                        while (event.changes.any { it.pressed }) {
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom().coerceFinite()
                            val rot = event.calculateRotation().coerceFinite()

                            viewModel.updatePixelAvatarPosition(
                                dxNorm = pan.x / boxSize.width.toFloat(),
                                dyNorm = pan.y / boxSize.height.toFloat()
                            )
                            viewModel.updatePixelAvatarScale(zoom)
                            viewModel.updatePixelAvatarRotation(rot)

                            event.changes.forEach { it.consume() }
                            event = awaitPointerEvent()
                        }
                        continue
                    }

                    // ------------------------------------------------------
                    // 3) EMPTY TAP — clear active sticker
                    // ------------------------------------------------------
                    if (!touchedStickerOnDown) {
                        viewModel.setActiveSticker(null)
                    }

                    while (event.changes.any { it.pressed }) {
                        event = awaitPointerEvent()
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------
// Helper
// ------------------------------------------------------
private fun Float.coerceFinite(): Float =
    if (this.isFinite()) this else 1f
