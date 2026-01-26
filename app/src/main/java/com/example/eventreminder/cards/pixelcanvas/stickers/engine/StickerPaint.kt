package com.example.eventreminder.cards.pixelcanvas.stickers.engine

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

object StickerPaint {
    // single paint instance for emoji/text stickers
    val textPaint: Paint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            // textSize will be used as-is by renderer (renderer adjusts drawing by bounds)
            textSize = 64f
            color = Color.BLACK
        }
    }

    fun getTextBounds(text: String): Rect {
        val r = Rect()
        textPaint.getTextBounds(text, 0, text.length, r)
        return r
    }
}