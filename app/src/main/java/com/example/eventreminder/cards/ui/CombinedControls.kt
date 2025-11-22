package com.example.eventreminder.cards.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.eventreminder.cards.model.BackgroundItem
import com.example.eventreminder.cards.model.StickerItem

/**
 * FINAL CombinedControls with proper category control
 */
@Composable
fun CombinedControls(
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
    backgroundItems: List<BackgroundItem>,
    onPredefinedBgSelected: (BackgroundItem) -> Unit,

    selectedCategory: Int,
    onCategorySelected: (Int) -> Unit,

    stickerItems: List<StickerItem>,
    onStickerClick: (StickerItem) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            "Customize",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 6.dp)
        )

        // ---- PICK / CLEAR ----
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            item { ControlTile("Pick", onPickBackground) }
            item { ControlTile("Clear", onClearBackground) }
        }

        // ---- CATEGORY CHIPS ----
        val labels = listOf("Images", "Smileys", "Hearts", "Party", "Misc")

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            items(labels.indices.toList()) { idx ->
                val selected = idx == selectedCategory

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onCategorySelected(idx) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(labels[idx])
                }
            }
        }

        // ---- STICKER LIST ----
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            items(stickerItems) { st ->
                if (st.resId != null) {
                    Image(
                        painter = painterResource(st.resId),
                        contentDescription = st.id,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onStickerClick(st) },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.LightGray.copy(alpha = 0.2f))
                            .clickable { onStickerClick(st) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(st.text ?: "", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlTile(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, textAlign = TextAlign.Center)
    }
}
