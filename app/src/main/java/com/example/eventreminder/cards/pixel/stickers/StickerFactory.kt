package com.example.eventreminder.cards.pixel.stickers

import android.content.Context
import android.graphics.BitmapFactory
import com.example.eventreminder.cards.pixel.stickers.StickerCatalogItem
import timber.log.Timber

/**
 * StickerFactory
 * Converts StickerCatalogItem -> StickerPx instance.
 * Later will contain:
 *  - decode bitmap if needed
 *  - or reference drawable id
 *  - or attach emoji text
 */
object StickerFactory {

    /**
     * Create a StickerPx instance.
     * Future: decode resource to bitmap if required.
     */
    fun createSticker(
        context: Context,
        catalogItem: StickerCatalogItem,
        startX: Float,
        startY: Float
    ): StickerPx {
        // TODO STEP-2: Implement bitmap decode for drawableResId
        // TODO STEP-3: Handle emoji text case
        // TODO STEP-4: Return final StickerPx with unique id

        Timber.tag("StickerFactory").d("createSticker() called for ${catalogItem.id}")

        return StickerPx(
            id = System.nanoTime(),         // temporary unique ID
            drawableResId = catalogItem.resId,
            bitmap = null,                  // will be filled later
            text = catalogItem.text,
            xNorm = startX,
            yNorm = startY,
            scale = 1f,
            rotationDeg = 0f
        )
    }
}
