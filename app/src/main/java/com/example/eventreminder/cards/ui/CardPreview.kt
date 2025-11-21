package com.example.eventreminder.cards.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eventreminder.cards.CardViewModel
import com.example.eventreminder.cards.model.CardData
import com.example.eventreminder.cards.model.CardSticker
import com.example.eventreminder.cards.model.EventKind
import timber.log.Timber

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardPreview"

// =============================================================
// PUBLIC API — Entry point for rendering card templates
// =============================================================

/**
 * CardPreview
 *
 * Decides which visual template to render based on eventKind.
 * Stickers are drawn on top of the template inside each card.
 */
@Composable
fun CardPreview(
    cardData: CardData,
    modifier: Modifier = Modifier,
    vm: CardViewModel
) {
    Timber.tag(TAG).d(
        "Rendering card → kind=%s id=%d",
        cardData.eventKind,
        cardData.reminderId
    )

    when (cardData.eventKind) {
        EventKind.BIRTHDAY -> BirthdayCard(cardData, modifier, vm)
        EventKind.ANNIVERSARY -> AnniversaryCard(cardData, modifier, vm)
        else -> GenericCard(cardData, modifier, vm)
    }
}

// =============================================================
// TEMPLATE: Birthday Card
// =============================================================
@Composable
private fun BirthdayCard(
    cardData: CardData,
    modifier: Modifier = Modifier,
    vm: CardViewModel
) {
    Surface(
        modifier = modifier
            .width(360.dp)
            .height(220.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(Modifier.fillMaxSize()) {

            // ----------- MAIN CONTENT -----------
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .align(Alignment.CenterStart)
            ) {

                Text(
                    text = cardData.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp)
                )

                cardData.name?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(text = it, style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Age", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = cardData.ageOrYearsLabel ?: "-",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    "Original: ${cardData.originalDateLabel}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Next: ${cardData.nextDateLabel}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ----------- TIMEZONE BADGE -----------
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.80f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(cardData.timezone.id, style = MaterialTheme.typography.bodySmall)
            }

            // ----------- STICKERS -----------
            RenderStickers(
                stickers = cardData.stickers,
                onDelete = { vm.removeSticker(it) },
                onUpdate = { vm.updateSticker(it) }
            )
        }
    }
}

// =============================================================
// TEMPLATE: Anniversary Card
// =============================================================
@Composable
private fun AnniversaryCard(
    cardData: CardData,
    modifier: Modifier = Modifier,
    vm: CardViewModel
) {
    Surface(
        modifier = modifier
            .width(360.dp)
            .height(220.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(Modifier.fillMaxSize()) {

            // ----------- MAIN CONTENT -----------
            Column(Modifier.padding(20.dp)) {

                Text(cardData.title, style = MaterialTheme.typography.headlineSmall)

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Years", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = cardData.ageOrYearsLabel ?: "-",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "Original: ${cardData.originalDateLabel}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Next: ${cardData.nextDateLabel}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ----------- STICKERS -----------
            RenderStickers(
                stickers = cardData.stickers,
                onDelete = { vm.removeSticker(it) },
                onUpdate = { vm.updateSticker(it) }
            )
        }
    }
}

// =============================================================
// TEMPLATE: Generic Card
// =============================================================
@Composable
private fun GenericCard(
    cardData: CardData,
    modifier: Modifier = Modifier,
    vm: CardViewModel
) {
    Surface(
        modifier = modifier
            .width(360.dp)
            .height(200.dp)
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        tonalElevation = 2.dp
    ) {
        Box(Modifier.fillMaxSize()) {

            // ----------- MAIN CONTENT -----------
            Column(Modifier.padding(16.dp)) {
                Text(cardData.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(cardData.originalDateLabel, style = MaterialTheme.typography.bodySmall)
                Text(cardData.nextDateLabel, style = MaterialTheme.typography.bodySmall)
            }

            // ----------- STICKERS -----------
            RenderStickers(
                stickers = cardData.stickers,
                onDelete = { vm.removeSticker(it) },
                onUpdate = { vm.updateSticker(it) }
            )
        }
    }
}

// =============================================================
// STICKER RENDERER
// =============================================================

/**
 * Renders all stickers on top of a card template.
 */
@Composable
fun RenderStickers(
    stickers: List<CardSticker>,
    onDelete: (CardSticker) -> Unit,
    onUpdate: (CardSticker) -> Unit
) {
    stickers.forEach { sticker ->
        DraggableSticker(
            sticker = sticker,
            onDelete = onDelete,
            onUpdate = onUpdate
        )
    }
}

// =============================================================
// DRAGGABLE + SCALABLE STICKER
// =============================================================

@Composable
fun DraggableSticker(
    sticker: CardSticker,
    onDelete: (CardSticker) -> Unit,
    onUpdate: (CardSticker) -> Unit
) {
    val density = LocalDensity.current

    var offsetX by remember { mutableStateOf(sticker.x) }
    var offsetY by remember { mutableStateOf(sticker.y) }
    var scale by remember { mutableStateOf(sticker.scale) }

    // Update ViewModel in real-time
    LaunchedEffect(offsetX, offsetY, scale) {
        sticker.x = offsetX
        sticker.y = offsetY
        sticker.scale = scale
        onUpdate(sticker)
    }

    // ----------- Gesture Handler -----------
    val gestureModifier = Modifier
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->

                // MOVE
                val dx = with(density) { pan.x.toDp().value }
                val dy = with(density) { pan.y.toDp().value }
                offsetX += dx
                offsetY += dy

                // SCALE
                scale = (scale * zoom).coerceIn(0.5f, 3.5f)
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { onDelete(sticker) }
            )
        }

    // ----------- Sticker Image -----------
    Image(
        painter = painterResource(sticker.drawableResId),
        contentDescription = null,
        modifier = Modifier
            .offset(offsetX.dp, offsetY.dp)
            .size((96 * scale).dp)
            .graphicsLayer { rotationZ = sticker.rotation }
            .then(gestureModifier)
    )
}
