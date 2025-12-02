package com.example.eventreminder.cards.pixel.stickers

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.example.eventreminder.cards.pixel.CardDataPx
import com.example.eventreminder.cards.pixel.CardSpecPx

/**
 * Sticker drawing module — called by PixelRenderer.
 */
object StickerDraw {

    private fun p(): Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawStickers(
        canvas: Canvas,
        spec: CardSpecPx,
        data: CardDataPx
    ) {
        // TODO: implement full drawing
        // Loop in order:
        // data.stickers.forEach { s -> ... }
        //
        // 1. Compute cx, cy
        // 2. Calculate intrinsic size (bitmap width or fixed dp for emoji)
        // 3. Build Matrix:
        //    m.postTranslate(-w/2, -h/2)
        //    m.postScale(s.scale, s.scale)
        //    m.postRotate(s.rotationDeg)
        //    m.postTranslate(cx, cy)
        //
        // 4. Draw bitmap OR draw emoji text
    }
}
