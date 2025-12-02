package com.example.eventreminder.cards.pixelcanvas.stickers.panel

// =============================================================
// StickerListPanel
// - Shows sticker previews for a selected category
// - Emits StickerCatalogItem on click
// =============================================================

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eventreminder.cards.pixelcanvas.stickers.StickerCatalogItem
import com.example.eventreminder.cards.pixelcanvas.stickers.StickerPreviewItem

@Composable
fun StickerListPanel(
    stickers: List<StickerCatalogItem>,
    onStickerSelected: (StickerCatalogItem) -> Unit
) {
    LazyRow(
        modifier = Modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(stickers) { item ->
            StickerPreviewItem(
                item = item,
                onClick = { onStickerSelected(item) }
            )
        }
    }

    Spacer(Modifier.height(20.dp))
}
