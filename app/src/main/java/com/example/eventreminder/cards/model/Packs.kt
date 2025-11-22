package com.example.eventreminder.cards.model


import androidx.annotation.DrawableRes
import com.example.eventreminder.R

/**
 * Small, lightweight packs used by StickerBar & BackgroundBar.
 * Keep packs simple arrays of BackgroundItem / StickerItem.
 */

data class StickerItem_1(
    val id: String,
    @DrawableRes val resId: Int? = null,
    val text: String? = null
)

object BackgroundPacks {
    // Populate with your drawable resource ids
    val defaultPack: List<BackgroundItem> = listOf(
        BackgroundItem("bg1", R.drawable.ic_birthday),
        //BackgroundItem("Confetti", resId = R.drawable.bg_confetti),
        //BackgroundItem("Balloons", resId = R.drawable.bg_balloons),
        //BackgroundItem("Floral", resId = R.drawable.bg_floral)
        // add as needed
    )
}

object StickerPacks {
    val birthdayPack: List<StickerItem> = listOf(
        StickerItem("cake1", R.drawable.ic_image2),  // your uploaded image
        StickerItem("cake2", R.drawable.ic_cake),    // optional extra
        StickerItem("balloons", R.drawable.ic_baloon1),
        StickerItem("party_hat", R.drawable.ic_cake2),
        StickerItem("confetti", R.drawable.ic_star),
        StickerItem("confetti", R.drawable.ic_birthday)
    )
}

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
