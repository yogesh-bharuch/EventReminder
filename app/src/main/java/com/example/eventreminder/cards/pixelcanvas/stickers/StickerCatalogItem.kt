package com.example.eventreminder.cards.pixel.stickers

import androidx.annotation.DrawableRes

/**
 * Catalog item shown in sticker selector rows.
 * NOT placed on card.
 */
data class StickerCatalogItem(
    val id: String,
    @DrawableRes val resId: Int? = null,
    val text: String? = null
)
