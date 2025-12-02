package com.example.eventreminder.cards.pixel.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import timber.log.Timber

/**
 * Global sticker bitmap cache.
 *
 * - We preload drawables ONCE using preload().
 * - After that, PixelRenderer can getBitmap(resId) without needing context.
 */
object StickerBitmapCache {

    private val cache = mutableMapOf<Int, Bitmap>()

    /** Preload one sticker (runs once per resId) */
    fun preload(context: Context, resId: Int) {
        if (cache.containsKey(resId)) return   // already loaded

        try {
            val drawable: Drawable = context.getDrawable(resId) ?: return

            // Handle WEBP/JPG where width/height may be 0
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 256
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 256

            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)

            cache[resId] = bmp

        } catch (t: Throwable) {
            Timber.e(t, "StickerBitmapCache preload failed for res $resId")
        }
    }

    /** Preload an entire pack */
    fun preloadPack(context: Context, pack: List<StickerCatalogItem>) {
        pack.forEach { item ->
            item.resId?.let { preload(context, it) }
        }
    }

    /** PixelRenderer calls this — NO context needed */
    fun getBitmap(resId: Int): Bitmap? = cache[resId]
}
