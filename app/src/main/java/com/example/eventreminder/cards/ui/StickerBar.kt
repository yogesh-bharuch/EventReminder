package com.example.eventreminder.cards.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.eventreminder.cards.model.StickerItem
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/**
 * Horizontal bar of stickers with category tabs above
 */
@Composable
fun StickerBar(
    items: List<StickerItem>,
    onStickerClick: (StickerItem) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // Thumbnail list
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            items(items) { sticker ->
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable { onStickerClick(sticker) },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(sticker.resId!!),
                        contentDescription = "sticker"
                    )
                }
            }
        }
    }
}
