package com.example.eventreminder.cards.model

import com.example.eventreminder.R

object EmojiStickerPack {

    val smileys = listOf(
        StickerItem(id = "smile1", text = "🙂"),
        StickerItem(id = "laugh1", text = "😂"),
        StickerItem(id = "heartEyes1", text = "😍"),
        StickerItem(id = "cool1", text = "😎"),
        StickerItem(id = "party1", text = "🥳"),
    )

    val hearts = listOf(
        StickerItem(id = "heartRed", text = "❤️"),
        StickerItem(id = "heartSparkle", text = "💖"),
        StickerItem(id = "twoHearts", text = "💕"),
        StickerItem(id = "heartBlue", text = "💙"),
        StickerItem(id = "heartGreen", text = "💚"),
    )

    val celebration = listOf(
        StickerItem(id = "fireworks", text = "🎆"),
        StickerItem(id = "confetti", text = "🎊"),
        StickerItem(id = "balloons", text = "🎈"),
        StickerItem(id = "gift", text = "🎁"),
        StickerItem(id = "cake", text = "🎂"),
    )

    val misc = listOf(
        StickerItem(id = "star", text = "⭐"),
        StickerItem(id = "sparkles", text = "✨"),
        StickerItem(id = "flower", text = "🌸"),
        StickerItem(id = "sun", text = "☀️"),
        StickerItem(id = "moon", text = "🌙"),
    )
}
