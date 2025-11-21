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
}
