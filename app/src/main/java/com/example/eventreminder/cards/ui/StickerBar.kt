package com.example.eventreminder.cards.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.eventreminder.cards.model.StickerItem

@Composable
fun StickerBar(
    items: List<StickerItem>,
    onStickerClick: (StickerItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEach { sticker ->
            Image(
                painter = painterResource(sticker.resId),
                contentDescription = sticker.id,
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onStickerClick(sticker) }
            )
        }
    }
}
