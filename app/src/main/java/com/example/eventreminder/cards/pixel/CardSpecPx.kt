package com.example.eventreminder.cards.pixel

// =============================================================
// CardSpecPx.kt (1080 x 1200 canonical spec)
// Layout A: Title + Name at top, large middle area, dates at bottom
// =============================================================

import timber.log.Timber

private const val TAG = "CardSpecPx"

data class CardSpecPx(
    val widthPx: Int,
    val heightPx: Int,
    val cornerRadiusPx: Int,
    val safeMarginPx: Int,
    val titleBox: RectPx,
    val nameBox: RectPx,
    val middleBox: RectPx,
    val dateBox: RectPx,
    val stickerLayer: RectPx,
    val backgroundLayer: RectPx
) {
    companion object {
        /**
         * Default tall card: 1080 x 1200 px.
         * Layout A:
         *  - titleBox: top reserved area
         *  - nameBox: below title
         *  - middleBox: large central area for stickers/avatars
         *  - dateBox: bottom row
         */
        fun default1080x1200(): CardSpecPx {
            val w = 1080
            val h = 1200
            val margin = 24

            // Title: top area ~120px high
            val titleBox = RectPx(margin, margin, w - margin * 2, 120)

            // Name: below title ~64px
            val nameBox = RectPx(margin, titleBox.y + titleBox.height + 8, w - margin * 2, 64)

            // Middle box: large free area for stickers / avatar (from below name to above dates)
            val middleTop = nameBox.y + nameBox.height + 12
            val dateBoxHeight = 56
            val middleBox = RectPx(
                margin,
                middleTop,
                w - margin * 2,
                h - margin - dateBoxHeight - middleTop - 8
            )

            // Date box: bottom bar
            val dateBox = RectPx(margin, h - margin - dateBoxHeight, w - margin * 2, dateBoxHeight)

            // Sticker layer covers the middle area (but could extend into safe margins)
            val stickerLayer = RectPx(margin, middleTop, w - margin * 2, (dateBox.y - middleTop))

            // background is full card
            val backgroundLayer = RectPx(0, 0, w, h)

            Timber.tag(TAG).d("Created spec 1080x1200: title=%s name=%s middle=%s date=%s",
                titleBox, nameBox, middleBox, dateBox)

            return CardSpecPx(
                widthPx = w,
                heightPx = h,
                cornerRadiusPx = 56,
                safeMarginPx = margin,
                titleBox = titleBox,
                nameBox = nameBox,
                middleBox = middleBox,
                dateBox = dateBox,
                stickerLayer = stickerLayer,
                backgroundLayer = backgroundLayer
            )
        }
    }
}
