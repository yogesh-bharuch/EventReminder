package com.example.eventreminder.cards.pixel.stickers

import com.example.eventreminder.cards.pixel.CardSpecPx

/**
 * Hit test if a touch lies inside a sticker
 * Uses matrix inverse mapping (TODO implement fully).
 */
object StickerHitTest {

    fun isTouchInsideSticker(
        touchX: Float,
        touchY: Float,
        spec: CardSpecPx,
        s: StickerPx
    ): Boolean {
        // TODO: compute:
        // - sticker center = spec.widthPx * s.xNorm, spec.heightPx * s.yNorm
        // - sticker intrinsic size
        // - build matrix (same as draw)
        // - invert matrix and map touch point → sticker-local coords
        // - check bounds 0..w, 0..h
        return false
    }
}
