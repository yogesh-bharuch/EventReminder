package com.example.eventreminder.cards.pixel

// =============================================================
// CardSpecPx.kt
// Canonical pixel specification for card layout (1080x720).
// =============================================================

import timber.log.Timber

private const val TAG = "CardSpecPx"

/**
 * Canonical card specification in pixels.
 *
 * This file defines default values for the canonical card used by
 * the PixelRenderer. You can create alternate specs if desired.
 */
data class CardSpecPx(
    val widthPx: Int,
    val heightPx: Int,
    val cornerRadiusPx: Int,
    val safeMarginPx: Int,
    val titleBox: RectPx,
    val avatarBox: RectPx,
    val nameBox: RectPx,
    val dateBox: RectPx,
    val stickerLayer: RectPx,
    val backgroundLayer: RectPx
) {
    companion object {
        /**
         * Recommended default spec: 1080 x 720 px (3:2)
         */
        fun default1080x720(): CardSpecPx {
            val w = 1080
            val h = 720
            val margin = 24
            // title area: top 140 px
            val titleBox = RectPx(margin, margin, w - margin * 2, 140)
            // avatar region: 180px diameter located at right-center within top half
            val avatarSize = 180
            val avatarBox = RectPx(w - margin - avatarSize, 80, avatarSize, avatarSize)
            // name area (below title)
            val nameBox = RectPx(margin, 170, w - margin * 2, 48)
            // date area: bottom row
            val dateBox = RectPx(margin, h - margin - 40, w - margin * 2, 40)
            // sticker layer covers most of the card, but keep small margins
            val stickerLayer = RectPx(margin, margin, w - margin * 2, h - margin * 2)
            // background layer is full card minus corner bleed
            val backgroundLayer = RectPx(0, 0, w, h)
            return CardSpecPx(
                widthPx = w,
                heightPx = h,
                cornerRadiusPx = 48,
                safeMarginPx = margin,
                titleBox = titleBox,
                avatarBox = avatarBox,
                nameBox = nameBox,
                dateBox = dateBox,
                stickerLayer = stickerLayer,
                backgroundLayer = backgroundLayer
            )
        }
    }

    init {
        Timber.tag(TAG).d("CardSpecPx created: %dx%d r=%d", widthPx, heightPx, cornerRadiusPx)
    }
}
