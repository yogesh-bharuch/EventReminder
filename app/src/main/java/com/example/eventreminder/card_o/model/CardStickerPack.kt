package com.example.eventreminder.card_o.model

import timber.log.Timber

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardStickerPack"

// =============================================================
/**
 * CardStickerPack
 *
 * A named collection of CardSticker objects that can be applied
 * to a CardRenderRequest. Packs are intended to be small presets
 * the user can choose from (confetti, hearts, birthday, stars).
 */
data class CardStickerPack(
    val id: String,              // unique id (ex: "birthday_confetti")
    val displayName: String,     // human-friendly name
    val stickers: List<CardSticker> // the actual sticker list
) {
    init {
        Timber.tag(TAG).d("StickerPack created: id=$id name=$displayName size=${stickers.size}")
    }
}
