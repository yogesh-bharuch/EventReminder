package com.example.eventreminder.cards.ui

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eventreminder.cards.model.CardData
import com.example.eventreminder.cards.model.CardSticker
import com.example.eventreminder.cards.model.EventKind
import timber.log.Timber

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardPreview"

// =============================================================
// PUBLIC API — Entry point for card templates
// =============================================================

/**
 * CardPreview
 *
 * Decides which card layout to render based on eventKind.
 * Stickers are handled inside each template.
 */
@Composable
fun CardPreview(
    cardData: CardData,
    modifier: Modifier = Modifier
) {
    Timber.tag(TAG)
        .d("Rendering card preview → kind=%s id=%d", cardData.eventKind, cardData.reminderId)

    when (cardData.eventKind) {
        EventKind.BIRTHDAY -> BirthdayCard(cardData, modifier)
        EventKind.ANNIVERSARY -> AnniversaryCard(cardData, modifier)
        else -> GenericCard(cardData, modifier)
    }
}

// =============================================================
// TEMPLATE: Birthday Card — Modern Flat UI (TODO-8 Style A)
// =============================================================

@Composable
private fun BirthdayCard(
    cardData: CardData,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(360.dp)
            .height(220.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ------------------------
            // MAIN CONTENT
            // ------------------------
            Column(modifier = Modifier
                    .padding(20.dp)
                    .align(Alignment.CenterStart)
            ) {
                Text(text = cardData.title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp))

                cardData.name?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = it, style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Age", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = cardData.ageOrYearsLabel ?: "-", style = MaterialTheme.typography.headlineSmall)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Original: ${cardData.originalDateLabel}", style = MaterialTheme.typography.bodySmall)
                Text("Next: ${cardData.nextDateLabel}", style = MaterialTheme.typography.bodySmall)
            }

            // ------------------------
            // TIMEZONE BADGE
            // ------------------------
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.80f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(text = cardData.timezone.id, style = MaterialTheme.typography.bodySmall)
            }

            // ------------------------
            // ⭐ STICKERS OVERLAY
            // ------------------------
            RenderStickers(cardData.stickers)
        }
    }
}

// =============================================================
// TEMPLATE: Anniversary Card — Modern Flat UI (TODO-8 Style A)
// =============================================================

@Composable
private fun AnniversaryCard(
    cardData: CardData,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(360.dp)
            .height(220.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // MAIN CONTENT
            Column(modifier = Modifier.padding(20.dp)) {

                Text(text = cardData.title, style = MaterialTheme.typography.headlineSmall)

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Years", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = cardData.ageOrYearsLabel ?: "-",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Original: ${cardData.originalDateLabel}", style = MaterialTheme.typography.bodySmall)
                Text("Next: ${cardData.nextDateLabel}", style = MaterialTheme.typography.bodySmall)
            }

            // ⭐ STICKERS
            RenderStickers(cardData.stickers)
        }
    }
}

// =============================================================
// TEMPLATE: Generic Card — Minimal Flat UI
// =============================================================

@Composable
private fun GenericCard(
    cardData: CardData,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(360.dp)
            .height(200.dp)
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        tonalElevation = 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // MAIN CONTENT
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = cardData.title, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = cardData.originalDateLabel, style = MaterialTheme.typography.bodySmall)
                Text(text = cardData.nextDateLabel, style = MaterialTheme.typography.bodySmall)
            }

            // ⭐ STICKERS
            RenderStickers(cardData.stickers)
        }
    }
}

// =============================================================
// INTERNAL — Sticker Renderer
// =============================================================

/**
 * Draw all stickers on top of the card.
 */
@Composable
private fun RenderStickers(stickers: List<CardSticker>) {
    stickers.forEach { sticker ->
        Image(
            painter = painterResource(id = sticker.drawableResId),
            contentDescription = null,
            modifier = Modifier
                .offset(sticker.x.dp, sticker.y.dp)
                .size(96.dp * sticker.scale)
                .graphicsLayer {
                    rotationZ = sticker.rotation
                }
        )
    }
}
