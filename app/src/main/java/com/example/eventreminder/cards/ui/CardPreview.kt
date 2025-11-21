package com.example.eventreminder.cards.ui

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
// PUBLIC API — CardPreview with avatar callback
// =============================================================
@Composable
fun CardPreview(
    cardData: CardData,
    modifier: Modifier = Modifier,
    vm: CardViewModel,
    onAvatarClick: () -> Unit
) {
    Timber.tag(TAG).d(
        "Rendering card → kind=%s id=%d",
        cardData.eventKind,
        cardData.reminderId
    )

    when (cardData.eventKind) {

        EventKind.BIRTHDAY ->
            BirthdayCard(cardData, modifier, vm, onAvatarClick)

        EventKind.ANNIVERSARY ->
            AnniversaryCard(cardData, modifier, vm, onAvatarClick)

        else ->
            GenericCard(cardData, modifier, vm, onAvatarClick)
    }
}

// =============================================================
// AVATAR COMPONENT — shared across templates (A1 style)
// =============================================================
@Composable
private fun AvatarInsideCard(
    avatar: android.graphics.Bitmap?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(start = 16.dp, top = 16.dp)
            .size(72.dp)
            .clip(CircleShape)
            .shadow(6.dp, CircleShape)
            .background(Color.White.copy(alpha = 0.6f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {

        if (avatar != null) {

            Image(
                bitmap = avatar.asImageBitmap(),
                contentDescription = "avatar",
                modifier = Modifier.clip(CircleShape)
            )

        } else {
            // Placeholder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.LightGray.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Text("Tap", fontSize = 12.sp)
            }
        }
    }
}

// =============================================================
// TEMPLATE: Birthday Card
// =============================================================
@Composable
private fun BirthdayCard(
    cardData: CardData,
    modifier: Modifier = Modifier,
    vm: CardViewModel,
    onAvatarClick: () -> Unit
) {
    val avatar = vm.avatarBitmap.collectAsState().value

    Surface(
        modifier = modifier
            .width(360.dp)
            .height(240.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {

        Box(Modifier.fillMaxSize()) {

            // AVATAR
            AvatarInsideCard(avatar, onAvatarClick)

            // MAIN CONTENT
            Column(
                modifier = Modifier
                    .padding(start = 100.dp, top = 22.dp, end = 20.dp)
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

            // TIMEZONE BADGE
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.80f)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(cardData.timezone.id, style = MaterialTheme.typography.bodySmall)
            }

            // STICKERS
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
    vm: CardViewModel,
    onAvatarClick: () -> Unit
) {

    val avatar = vm.avatarBitmap.collectAsState().value

    Surface(
        modifier = modifier
            .width(360.dp)
            .height(240.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {

        Box(Modifier.fillMaxSize()) {

            AvatarInsideCard(avatar, onAvatarClick)

            Column(
                modifier = Modifier
                    .padding(start = 100.dp, top = 20.dp, end = 20.dp)
            ) {

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
    vm: CardViewModel,
    onAvatarClick: () -> Unit
) {
    val avatar = vm.avatarBitmap.collectAsState().value

    Surface(
        modifier = modifier
            .width(360.dp)
            .height(220.dp)
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        tonalElevation = 2.dp
    ) {

        Box(Modifier.fillMaxSize()) {

            AvatarInsideCard(avatar, onAvatarClick)

            Column(
                modifier = Modifier
                    .padding(start = 100.dp, top = 20.dp, end = 20.dp)
            ) {
                Text(cardData.title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(cardData.originalDateLabel, style = MaterialTheme.typography.bodySmall)
                Text(cardData.nextDateLabel, style = MaterialTheme.typography.bodySmall)
            }

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
// DRAGGABLE + SCALABLE STICKER (Image + Emoji)
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

    // Update model → VM
    LaunchedEffect(offsetX, offsetY, scale) {
        sticker.x = offsetX
        sticker.y = offsetY
        sticker.scale = scale
        onUpdate(sticker)
    }

    val gestureModifier =
        Modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->

                    val dx = with(density) { pan.x.toDp().value }
                    val dy = with(density) { pan.y.toDp().value }

                    offsetX += dx
                    offsetY += dy

                    scale = (scale * zoom).coerceIn(0.5f, 3.5f)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onDelete(sticker) }
                )
            }

    // IMAGE STICKER
    if (sticker.drawableResId != null) {
        Image(
            painter = painterResource(sticker.drawableResId),
            contentDescription = null,
            modifier = Modifier
                .offset(offsetX.dp, offsetY.dp)
                .size((96 * scale).dp)
                .graphicsLayer { rotationZ = sticker.rotation }
                .then(gestureModifier)
        )
        return
    }

    // TEXT / EMOJI STICKER
    if (sticker.text != null) {
        Text(
            text = sticker.text,
            fontSize = (40 * scale).sp,
            modifier = Modifier
                .offset(offsetX.dp, offsetY.dp)
                .graphicsLayer { rotationZ = sticker.rotation }
                .then(gestureModifier)
        )
    }
}
