package com.example.eventreminder.cards.model

import java.util.UUID

// =============================================================
// CardSticker
// =============================================================

/**
 * CardSticker
 *
 * A universal sticker model. A sticker can be:
 *  - image sticker: drawableResId != null
 *  - text sticker: text != null (emoji, letter, number, or phrase)
 *
 * Mutable fields (x,y,scale,rotation) are updated during interaction.
 * id is stable and used for equality and updates.
 */

data class CardSticker(
    val id: String = UUID.randomUUID().toString(),
    val drawableResId: Int? = null,
    val text: String? = null,
    var x: Float = 0f,
    var y: Float = 0f,
    var scale: Float = 1f,
    var rotation: Float = 0f
)
