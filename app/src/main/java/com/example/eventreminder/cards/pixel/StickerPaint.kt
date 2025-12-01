package com.example.eventreminder.cards.pixel

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/**
 * Paints and helpers for drawing text/emoji stickers.
 */
object StickerPaint {
    // single shared paint for emoji/text stickers (adjust size later if needed)
    val textPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        textSize = 72f
        color = android.graphics.Color.parseColor("#222222")
        style = Paint.Style.FILL
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.LEFT
    }

    private val tmpRect = Rect()

    /**
     * Returns the pixel bounds Rect for given text using textPaint.
     * This helps center drawing in the renderer: you call getTextBounds and use center offsets.
     */
    fun getTextBounds(text: String): Rect {
        synchronized(tmpRect) {
            tmpRect.setEmpty()
            textPaint.getTextBounds(text, 0, text.length, tmpRect)
            return Rect(tmpRect)
        }
    }

    /**
     * Convenience to set size when rendering large/small emoji.
     */
    fun setTextSize(px: Float) {
        textPaint.textSize = px
    }
}
