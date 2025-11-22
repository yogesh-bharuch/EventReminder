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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
 * FINAL — clean, error-free CombinedControls
 * - Background Pick/Clear + Predefined thumbnails
 * - Category chips row
 * - Sticker bar row
 */
@Composable
fun CombinedControls(
    onPickBackground: () -> Unit,
    onClearBackground: () -> Unit,
    backgroundItems: List<BackgroundItem>,
    onPredefinedBgSelected: (BackgroundItem) -> Unit,
    stickerItems: List<StickerItem>,
    onStickerClick: (StickerItem) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // -------------------------------------------------------
        // SECTION LABEL
        // -------------------------------------------------------
        Text(
            "Customize",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 6.dp)
        )

        // -------------------------------------------------------
        // ROW 1 → Pick / Clear + Predefined Backgrounds
        // -------------------------------------------------------
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {

            // Pick
            item {
                ControlTile(label = "Pick") { onPickBackground() }
            }

            // Clear
            item {
                ControlTile(label = "Clear") { onClearBackground() }
            }

            // Predefined backgrounds
            items(backgroundItems) { bg ->
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.LightGray.copy(alpha = 0.25f))
                        .clickable { onPredefinedBgSelected(bg) }
                ) {
                    Image(
                        painter = painterResource(bg.resId),
                        contentDescription = bg.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // -------------------------------------------------------
        // ROW 2 → Category Chips
        // -------------------------------------------------------
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
                        .clickable { selectedCategory = idx }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(labels[idx], color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        // -------------------------------------------------------
        // ROW 3 → Sticker Thumbnails
        // -------------------------------------------------------
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
                        Text(
                            text = st.text ?: "",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }
    }
}

/**
 * Small reusable tile for Pick / Clear
 */
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
