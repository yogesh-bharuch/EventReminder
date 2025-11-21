package com.example.eventreminder.cards.model

import androidx.annotation.DrawableRes

data class StickerPack(
    val id: String,
    val name: String,
    val items: List<StickerItem>
)

data class StickerItem(
    val id: String,
    @DrawableRes val resId: Int
)
