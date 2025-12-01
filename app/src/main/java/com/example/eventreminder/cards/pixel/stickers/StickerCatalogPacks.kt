package com.example.eventreminder.cards.pixel.stickers

import com.example.eventreminder.R

object StickerCatalogPacks {

    /*val birthdayPack: List<StickerCatalogItem> = listOf(
        StickerCatalogItem("cake1", R.drawable.ic_image2),
        StickerCatalogItem("cake2", R.drawable.ic_cake),
        StickerCatalogItem("balloons", R.drawable.ic_baloon1),
        StickerCatalogItem("party_hat", R.drawable.ic_cake2),
        StickerCatalogItem("confetti1", R.drawable.ic_star),
        StickerCatalogItem("confetti2", R.drawable.ic_birthday)
    )*/

    val smileys = listOf(
        StickerCatalogItem("smile1", text = "🙂"),
        StickerCatalogItem("laugh1", text = "😂"),
        StickerCatalogItem("heartEyes1", text = "😍"),
        StickerCatalogItem("cool1", text = "😎"),
        StickerCatalogItem("party1", text = "🥳"),
    )

    val hearts = listOf(
        StickerCatalogItem("heartRed", text = "❤️"),
        StickerCatalogItem("heartSparkle", text = "💖"),
        StickerCatalogItem("twoHearts", text = "💕"),
        StickerCatalogItem("heartBlue", text = "💙"),
        StickerCatalogItem("heartGreen", text = "💚"),
    )

    val celebration = listOf(
        StickerCatalogItem("fireworks", text = "🎆"),
        StickerCatalogItem("confetti", text = "🎊"),
        StickerCatalogItem("balloons", text = "🎈"),
        StickerCatalogItem("gift", text = "🎁"),
        StickerCatalogItem("cake", text = "🎂"),
    )

    val misc = listOf(
        StickerCatalogItem("star", text = "⭐"),
        StickerCatalogItem("sparkles", text = "✨"),
        StickerCatalogItem("flower", text = "🌸"),
        StickerCatalogItem("sun", text = "☀️"),
        StickerCatalogItem("moon", text = "🌙"),
    )

    fun getPack(category: StickerCategory): List<StickerCatalogItem> {
        return when (category) {
            StickerCategory.Smileys -> smileys
            StickerCategory.Hearts -> hearts
            StickerCategory.Celebration -> celebration
            StickerCategory.Misc -> misc
        }
    }

}
