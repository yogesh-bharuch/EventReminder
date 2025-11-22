package com.example.eventreminder.card.model

import timber.log.Timber

private const val TAG_EXT = "CardStickerExt"

/**
 * Apply sticker pack (by pack id) to a CardRenderRequest, returning a new copy.
 */
fun CardRenderRequest.withStickerPack(packId: String): CardRenderRequest {
    val pack = StickerPackRepository.allPacks.firstOrNull { it.id == packId }
    if (pack == null) {
        Timber.tag(TAG_EXT).w("StickerPack not found: $packId")
        return this
    }
    Timber.tag(TAG_EXT).d("Applying StickerPack: ${pack.id}")
    return this.copy(stickers = pack.stickers)
}
