package com.example.eventreminder.card_o.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.eventreminder.card_o.model.StickerPackRepository
import timber.log.Timber

// =============================================================
// Constants
// =============================================================
private const val TAG = "StickerPackPicker"

/**
 * StickerPackPicker
 *
 * Simple horizontal picker that displays preview tiles for each
 * built-in sticker pack. Clicking a tile will call onSelect(packId).
 *
 * This component is intentionally minimal: preview uses the first
 * sticker from the pack as thumbnail.
 */
@Composable
fun StickerPackPicker(
    onSelect: (packId: String) -> Unit
) {
    val context = LocalContext.current
    val packs = remember { StickerPackRepository.allPacks }

    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (pack in packs) {
            Card(
                modifier = Modifier
                    .size(140.dp)
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        Timber.tag(TAG).d("StickerPack selected: ${pack.id}")
                        onSelect(pack.id)
                    },
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // If pack has at least one sticker, show its drawable as preview
                    val first = pack.stickers.firstOrNull()
                    if (first != null) {
                        Image(
                            painter = painterResource(id = first.drawableResId),
                            contentDescription = pack.displayName,
                            modifier = Modifier.size(64.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(64.dp))
                    }

                    Text(text = pack.displayName, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
