package com.example.eventreminder.cards.model

import com.example.eventreminder.R

object StickerPacks {

    val birthdayPack = StickerPack(
        id = "birthday_pack",
        name = "Birthday",
        items = listOf(
            StickerItem("cake1", R.drawable.ic_image2),  // your uploaded image
            StickerItem("cake2", R.drawable.ic_cake),    // optional extra
            StickerItem("balloons", R.drawable.ic_baloon1),
            StickerItem("party_hat", R.drawable.ic_cake2),
            StickerItem("confetti", R.drawable.ic_star)
        )
    )

    val default = listOf(birthdayPack)

    val emojiPack = StickerPack(
        id = "emoji_pack",
        name = "Emoji",
        items = listOf(
            StickerItem(id = "e1", text = "🎂"),
            StickerItem(id = "e2", text = "🎉"),
            StickerItem(id = "e3", text = "🎈"),
            StickerItem(id = "e4", text = "✨"),
            StickerItem(id = "e5", text = "💖"),
            StickerItem(id = "e6", text = "🥳")
        )
    )

    val lettersPack = StickerPack(
        id = "letters_pack",
        name = "Letters",
        items = ('A'..'Z').map { ch ->
            StickerItem(id = "l_$ch", text = ch.toString())
        }
    )

    val numbersPack = StickerPack(
        id = "numbers_pack",
        name = "Numbers",
        items = ('0'..'9').map { n ->
            StickerItem(id = "n_$n", text = n.toString())
        }
    )

    val allPacks = listOf(birthdayPack, emojiPack, lettersPack, numbersPack)
}
