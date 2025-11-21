package com.example.eventreminder.cards.model


data class StickerItem(
    val id: String,
    val resId: Int? = null,   // image (optional)
    val text: String? = null  // emoji / letters / numbers / phrases (optional)
)
