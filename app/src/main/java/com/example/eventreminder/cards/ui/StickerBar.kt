package com.example.eventreminder.cards.ui

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eventreminder.cards.model.EmojiStickerPack
import com.example.eventreminder.cards.model.StickerItem

// =============================================================
// Public Composable: StickerBar
// =============================================================

@Composable
fun StickerBar(
    items: List<StickerItem>,       // your image pack
    onStickerClick: (StickerItem) -> Unit
) {
    // ---------------------------------------------------------
    // Categories (LazyRow selector)
    // ---------------------------------------------------------
    val categories = listOf(
        "Images",
        "Smileys",
        "Hearts",
        "Party",
        "Misc"
    )

    var selectedCategory by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {

        // =====================================================
        // CATEGORY PICKER (LazyRow)
        // =====================================================
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            items(categories) { label ->
                val index = categories.indexOf(label)
                val selected = index == selectedCategory

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { selectedCategory = index }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 15.sp,
                        color = if (selected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // =====================================================
        // CONTENT BY CATEGORY
        // =====================================================
        when (selectedCategory) {

            0 -> ImageStickerRow(items, onStickerClick)
            1 -> EmojiStickerRow(EmojiStickerPack.smileys, onStickerClick)
            2 -> EmojiStickerRow(EmojiStickerPack.hearts, onStickerClick)
            3 -> EmojiStickerRow(EmojiStickerPack.celebration, onStickerClick)
            4 -> EmojiStickerRow(EmojiStickerPack.misc, onStickerClick)
        }
    }
}

// =============================================================
// Category Emoji Mapping
// =============================================================
private fun categoryEmoji(name: String): String = when (name) {
    "Images" -> "🖼️"
    "Smileys" -> "🙂"
    "Hearts" -> "❤️"
    "Party" -> "🎉"
    "Misc" -> "⭐"
    else -> "✨"
}

// =============================================================
// IMAGE STICKER ROW
// =============================================================

@Composable
private fun ImageStickerRow(
    items: List<StickerItem>,
    onStickerClick: (StickerItem) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items) { sticker ->
            if (sticker.resId != null) {
                Image(
                    painter = painterResource(sticker.resId),
                    contentDescription = sticker.id,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onStickerClick(sticker) }
                )
            }
        }
    }
}

// =============================================================
// EMOJI STICKER ROW
// =============================================================

@Composable
private fun EmojiStickerRow(
    emojis: List<StickerItem>,
    onStickerClick: (StickerItem) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(emojis) { sticker ->

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onStickerClick(sticker) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sticker.text ?: "",
                    fontSize = 32.sp
                )
            }
        }
    }
}











/*
package com.example.eventreminder.cards.ui

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eventreminder.cards.model.EmojiStickerPack
import com.example.eventreminder.cards.model.StickerItem

// =============================================================
// Public Composable: StickerBar
// =============================================================

@Composable
fun StickerBar(
    items: List<StickerItem>,     // existing image pack (birthday pack)
    onStickerClick: (StickerItem) -> Unit
) {
    // ---------------------------------------------------------
    // Tabs: Images + Emoji packs
    // ---------------------------------------------------------
    val tabs = listOf("Images", "Smileys", "Hearts", "Party", "Misc")

    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {

        // ---------------- TAB STRIP ----------------
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, label ->
                Tab(
                    selected = index == selectedTab,
                    onClick = { selectedTab = index },
                    text = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---------------- TAB CONTENT ----------------
        when (selectedTab) {

            // ---------------------------------------------------------
            // 0 — IMAGE STICKERS (your current working items)
            // ---------------------------------------------------------
            0 -> ImageStickerRow(
                items = items,
                onStickerClick = onStickerClick
            )

            // ---------------------------------------------------------
            // 1 — SMILEYS
            // ---------------------------------------------------------
            1 -> EmojiStickerRow(
                emojis = EmojiStickerPack.smileys,
                onStickerClick = onStickerClick
            )

            // ---------------------------------------------------------
            // 2 — HEARTS
            // ---------------------------------------------------------
            2 -> EmojiStickerRow(
                emojis = EmojiStickerPack.hearts,
                onStickerClick = onStickerClick
            )

            // ---------------------------------------------------------
            // 3 — PARTY / CELEBRATION
            // ---------------------------------------------------------
            3 -> EmojiStickerRow(
                emojis = EmojiStickerPack.celebration,
                onStickerClick = onStickerClick
            )

            // ---------------------------------------------------------
            // 4 — MISC
            // ---------------------------------------------------------
            4 -> EmojiStickerRow(
                emojis = EmojiStickerPack.misc,
                onStickerClick = onStickerClick
            )
        }
    }
}

// =============================================================
// Image Sticker Row (Existing stickers)
// =============================================================

@Composable
private fun ImageStickerRow(
    items: List<StickerItem>,
    onStickerClick: (StickerItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEach { sticker ->
            if (sticker.resId != null) {
                Image(
                    painter = painterResource(sticker.resId),
                    contentDescription = sticker.id,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onStickerClick(sticker) }
                )
            }
        }
    }
}

// =============================================================
// Emoji Sticker Row (Option-C Style)
// =============================================================

@Composable
private fun EmojiStickerRow(
    emojis: List<StickerItem>,
    onStickerClick: (StickerItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        emojis.forEach { sticker ->

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onStickerClick(sticker) },
                contentAlignment = Alignment.Center
            ) {
                // Render Text Emoji
                Text(
                    text = sticker.text ?: "",
                    fontSize = 32.sp
                )
            }
        }
    }
}
*/
