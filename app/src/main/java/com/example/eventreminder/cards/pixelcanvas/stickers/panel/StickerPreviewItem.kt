package com.example.eventreminder.cards.pixelcanvas.stickers.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.eventreminder.cards.pixelcanvas.stickers.model.StickerCatalogItem

/**
 * Small preview tile used inside the sticker selector LazyRow.
 * Shows either a drawable (resId) or an emoji/text.
 */
@Composable
fun StickerPreviewItem(
    item: StickerCatalogItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .padding(4.dp)
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF2F2F2))
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
            when {
                item.resId != null -> {
                    // show image resource
                    Image(
                        painter = painterResource(id = item.resId),
                        contentDescription = item.id,
                        modifier = Modifier.size(44.dp)
                    )
                }
                !item.text.isNullOrEmpty() -> {
                    // show emoji/text
                    Text(
                        text = item.text,
                        fontSize = 28.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                    )
                }
                else -> {
                    Text(text = "?", fontSize = 20.sp)
                }
            }
        }
    }
}
