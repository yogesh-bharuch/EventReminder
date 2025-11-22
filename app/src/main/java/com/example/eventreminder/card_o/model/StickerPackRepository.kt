package com.example.eventreminder.card.model

import timber.log.Timber
import com.example.eventreminder.R

// =============================================================
// Constants
// =============================================================
private const val TAG = "StickerPackRepository"

/**
 * StickerPackRepository
 *
 * Provides built-in sticker packs for debug and initial UX.
 * Replace drawableResId values with app resources for production.
 */
object StickerPackRepository {

    // ---------------------------------------------------------
    // Public API: list of available packs
    // ---------------------------------------------------------
    val allPacks: List<CardStickerPack> by lazy {
        listOf(confettiPack(), birthdayPack(), heartsPack(), starsPack(), emojiPack(), winterPack())
    }

    // ---------------------------------------------------------
    // Confetti Pack
    // ---------------------------------------------------------
    private fun confettiPack(): CardStickerPack {
        val stickers = listOf(
            CardSticker(R.drawable.ic_star, 40f, 40f, scale = 1.1f, rotation = -20f),
            CardSticker(R.drawable.ic_star1, 260f, 90f, scale = 0.9f, rotation = 12f),
            CardSticker(R.drawable.ic_cake, 760f, 120f, scale = 1.2f, rotation = 8f),
            CardSticker(R.drawable.ic_cake1, 420f, 680f, scale = 1.0f, rotation = -6f)
        )
        return CardStickerPack(id = "confetti", displayName = "Confetti Pack", stickers = stickers)
    }

    // ---------------------------------------------------------
    // Birthday Pack
    // ---------------------------------------------------------
    private fun birthdayPack(): CardStickerPack {

        // Normalized scale for big PNGs (20% of card width)
        val balloonScale = 0.02f     // balloons usually large
        val starScale    = 0.12f     // stars should be small

        val stickers = listOf(

            // 🎈 Balloon at top right
            CardSticker(drawableResId = R.drawable.ic_baloon1, x = 800f, y = 80f, scale = balloonScale),
            // ⭐ Star bottom-left
            CardSticker(drawableResId = R.drawable.star_1, x = 120f, y = 760f, scale = starScale),
            // ⭐ Second star (mid-right)
            CardSticker(drawableResId = R.drawable.star_2, x = 420f, y = 520f, scale = starScale)
        )

        return CardStickerPack(id = "birthday", displayName = "Birthday Pack", stickers = stickers)
    }


    // ---------------------------------------------------------
    // Hearts Pack
    // ---------------------------------------------------------
    private fun heartsPack(): CardStickerPack {
        val stickers = listOf(
            CardSticker(R.drawable.ic_cake, 80f, 200f, scale = 1.4f, rotation = -10f),
            CardSticker(R.drawable.ic_cake, 640f, 260f, scale = 1.2f, rotation = 18f),
            CardSticker(R.drawable.ic_cake1, 480f, 720f, scale = 0.9f)
        )
        return CardStickerPack(id = "hearts", displayName = "Hearts Pack", stickers = stickers)
    }

    // ---------------------------------------------------------
    // Stars Pack
    // ---------------------------------------------------------
    private fun starsPack(): CardStickerPack {
        val stickers = listOf(
            CardSticker(R.drawable.ic_baloon1, 100f, 100f, scale = 1.6f, rotation = -12f),
            CardSticker(R.drawable.ic_baloon1, 360f, 80f, scale = 1.0f, rotation = 6f),
            CardSticker(R.drawable.ic_cake, 820f, 220f, scale = 1.3f)
        )
        return CardStickerPack(id = "stars", displayName = "Stars Pack", stickers = stickers)
    }

    // ---------------------------------------------------------
    // Emoji Pack
    // ---------------------------------------------------------
    private fun emojiPack(): CardStickerPack {
        val stickers = listOf(
            CardSticker(R.drawable.ic_baloon1, 100f, 100f, scale = 1.2f),
            CardSticker(R.drawable.ic_baloon1, 300f, 400f, scale = 1.0f),
            CardSticker(R.drawable.ic_baloon1, 600f, 200f, scale = 1.3f)
        )
        return CardStickerPack(id = "emoji", displayName = "Emoji Pack", stickers = stickers)
    }

    // ---------------------------------------------------------
    // winter Pack
    // ---------------------------------------------------------
    private fun winterPack(): CardStickerPack {
        val stickers = listOf(
            CardSticker(R.drawable.ic_cake, 120f, 80f, scale = 1.1f, rotation = -10f),
            CardSticker(R.drawable.ic_cake1, 500f, 600f, scale = 1.0f),
            CardSticker(R.drawable.ic_baloon1, 300f, 300f, scale = 1.2f)
        )
        return CardStickerPack(id = "winter", displayName = "Winter Pack", stickers = stickers)
    }
}
