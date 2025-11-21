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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
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
// PUBLIC API — CardPreview with avatar support
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
// AVATAR DRAGGABLE — Mode A (single avatar)
// =============================================================
@Composable
private fun AvatarDraggable(
    avatarBitmap: android.graphics.Bitmap?,
    vm: CardViewModel,
    onClick: () -> Unit
) {
    // Read transform state from VM
    val xDp by vm.avatarOffsetX.collectAsState()
    val yDp by vm.avatarOffsetY.collectAsState()
    val scale by vm.avatarScale.collectAsState()
    val rotation by vm.avatarRotation.collectAsState()

    val density = LocalDensity.current

    // Local gesture state
    var localOffsetX by remember { mutableStateOf(xDp) }
    var localOffsetY by remember { mutableStateOf(yDp) }
    var localScale by remember { mutableStateOf(scale) }
    var localRotation by remember { mutableStateOf(rotation) }

    // Sync local -> VM when transforms change
    LaunchedEffect(localOffsetX, localOffsetY, localScale, localRotation) {
        vm.updateAvatarTransform(localOffsetX, localOffsetY, localScale, localRotation)
    }

    Box(
        modifier = Modifier
            .offset(localOffsetX.dp, localOffsetY.dp)
            .size((72 * localScale).dp)
            .clip(CircleShape)
            .shadow(6.dp, CircleShape)
            .background(Color.White.copy(alpha = 0.6f))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rotationArc ->
                    // Convert pan in pixels to dp using density
                    val dxDp = with(density) { pan.x.toDp().value }
                    val dyDp = with(density) { pan.y.toDp().value }

                    localOffsetX += dxDp
                    localOffsetY += dyDp

                    localScale = (localScale * zoom).coerceIn(0.4f, 3.0f)
                    localRotation += rotationArc
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() /* open picker if needed */ })
            },
        contentAlignment = Alignment.Center
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap.asImageBitmap(),
                contentDescription = "avatar",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = localRotation }
                    .clip(CircleShape)
            )
        } else {
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
// TEMPLATE: BirthdayCard (uses AvatarDraggable inside)
// =============================================================
@Composable
private fun BirthdayCard(
    cardData: CardData,
    modifier: Modifier = Modifier,
    vm: CardViewModel,
    onAvatarClick: () -> Unit
) {
    val avatarBmp by vm.avatarBitmap.collectAsState()

    Surface(
        modifier = modifier
            .width(360.dp)
            .height(240.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(Modifier.fillMaxSize()) {

            // Avatar draggable (top-left inside)
            AvatarDraggable(avatarBitmap = avatarBmp, vm = vm, onClick = onAvatarClick)

            // Main content shifted right to avoid avatar overlap
            Column(
                modifier = Modifier
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp)
            ) {
                Text(
                    text = "Haapy " + cardData.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp)
                )
                cardData.name?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(text = it, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(12.dp))
                // YEARS BADGE (glowing circle)
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .graphicsLayer {
                            shadowElevation = 24.dp.toPx()   // strong glow
                            shape = CircleShape
                            clip = false
                        }
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cardData.ageOrYearsLabel ?: "-",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Spacer(Modifier.height(55.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "BirthDate: ${cardData.originalDateLabel}", style = MaterialTheme.typography.labelSmall)

                    Spacer(modifier = Modifier.weight(1f)) // pushes next text to right

                    Text(text = "Yogesh", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                }

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
// TEMPLATE: AnniversaryCard (uses AvatarDraggable)
// =============================================================
@Composable
private fun AnniversaryCard(
    cardData: CardData,
    modifier: Modifier = Modifier,
    vm: CardViewModel,
    onAvatarClick: () -> Unit
) {
    val avatarBmp by vm.avatarBitmap.collectAsState()

    Surface(
        modifier = modifier
            .width(360.dp)
            .height(240.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(Modifier.fillMaxSize()) {

            AvatarDraggable(avatarBmp, vm, onAvatarClick)

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
                Text("Original: ${cardData.originalDateLabel}", style = MaterialTheme.typography.bodySmall)
                Text("Next: ${cardData.nextDateLabel}", style = MaterialTheme.typography.bodySmall)
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
// TEMPLATE: GenericCard (uses AvatarDraggable)
// =============================================================
@Composable
private fun GenericCard(
    cardData: CardData,
    modifier: Modifier = Modifier,
    vm: CardViewModel,
    onAvatarClick: () -> Unit
) {
    val avatarBmp by vm.avatarBitmap.collectAsState()

    Surface(
        modifier = modifier
            .width(360.dp)
            .height(220.dp)
            .shadow(6.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp)),
        tonalElevation = 2.dp
    ) {
        Box(Modifier.fillMaxSize()) {

            AvatarDraggable(avatarBmp, vm, onAvatarClick)

            Column(
                modifier = Modifier.padding(start = 100.dp, top = 20.dp, end = 20.dp)
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
// STICKER RENDERER + DRAGGABLE (unchanged)
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

    LaunchedEffect(offsetX, offsetY, scale) {
        sticker.x = offsetX
        sticker.y = offsetY
        sticker.scale = scale
        onUpdate(sticker)
    }

    val gestureModifier = Modifier
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
            detectTapGestures(onLongPress = { onDelete(sticker) })
        }

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
