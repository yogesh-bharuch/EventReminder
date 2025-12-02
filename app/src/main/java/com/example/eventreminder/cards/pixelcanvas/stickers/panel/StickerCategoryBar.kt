package com.example.eventreminder.cards.pixelcanvas.stickers.panel

// =============================================================
// StickerCategoryBar
// - Horizontal row of category buttons
// - Emits category selection to parent
// =============================================================

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eventreminder.cards.pixelcanvas.stickers.StickerCategory

@Composable
fun StickerCategoryBar(
    categories: List<StickerCategory>,
    selectedCategory: StickerCategory?,
    onSelect: (StickerCategory?) -> Unit
) {
    LazyRow(
        modifier = Modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(categories) { cat ->
            Button(
                onClick = {
                    // Toggle: tap again → collapse
                    onSelect(if (selectedCategory == cat) null else cat)
                }
            ) {
                Text(cat.name)
            }
        }
    }
}
