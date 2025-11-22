package com.example.eventreminder.cards.ui

// =============================================================
// Imports
// =============================================================
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
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
// PUBLIC API — CardPreview with background-photo picker support
// — now uses ViewModel persisted background state (B1: auto-fit)
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

    val context = LocalContext.current

    // Use ViewModel persisted background
    val bgBitmap by vm.backgroundBitmap.collectAsState()

    // Background picker launcher (calls VM to persist)
    val bgPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            Timber.tag(TAG).d("Background picked → $uri")
            vm.onBackgroundImageSelected(context, uri)
        } else {
            Timber.tag(TAG).d("No background selected")
        }
    }

    // Small BackgroundBar integrated above card (pick / clear)
    Column(modifier = modifier) {
        BackgroundBar(
            onPick = {
                bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onClear = {
                vm.clearBackground()
            }
        )

        // Card rendering area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .heightIn(min = 200.dp),
            contentAlignment = Alignment.TopCenter
        ) {

            // Background (auto-fit: ContentScale.Crop semantics approximated by downsample + match size)
            if (bgBitmap != null) {
                Image(
                    bitmap = bgBitmap!!.asImageBitmap(),
                    contentDescription = "Card background",
                    modifier = Modifier
                        .width(360.dp)
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            // Template on top (transparent surfaces to reveal background)
            when (cardData.eventKind) {
                EventKind.BIRTHDAY ->
                    BirthdayCard(cardData = cardData, modifier = Modifier, vm = vm, onAvatarClick = onAvatarClick)
                EventKind.ANNIVERSARY ->
                    AnniversaryCard(cardData = cardData, modifier = Modifier, vm = vm, onAvatarClick = onAvatarClick)
                else ->
                    GenericCard(cardData = cardData, modifier = Modifier, vm = vm, onAvatarClick = onAvatarClick)
            }
        }
    }
}

// =============================================================
// BackgroundBar — small UI to pick/clear photo backgrounds
// =============================================================
@Composable
private fun BackgroundBar(
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pick tile
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectTapGestures { onPick() }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("Pick", style = MaterialTheme.typography.labelMedium)
        }

        // Clear tile
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectTapGestures { onClear() }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("Clear", style = MaterialTheme.typography.labelMedium)
        }

        Spacer(modifier = Modifier.weight(1f))
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
        color = Color.Transparent // keep transparent to show bg under it
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
                    text = "Happy ${cardData.title}",
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BirthDate: ${cardData.originalDateLabel}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.weight(1f)) // pushes next text to right
                    Text(
                        text = "Yogesh",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.End
                    )
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
        color = Color.Transparent
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
        tonalElevation = 2.dp,
        color = Color.Transparent
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

    // Local mutable state for interactive transforms
    var offsetX by remember { mutableStateOf(sticker.x) }
    var offsetY by remember { mutableStateOf(sticker.y) }
    var scale by remember { mutableStateOf(sticker.scale) }
    var rotation by remember { mutableStateOf(sticker.rotation) }

    // Sync local state back to the sticker + notify VM when changed
    LaunchedEffect(offsetX, offsetY, scale, rotation) {
        sticker.x = offsetX
        sticker.y = offsetY
        sticker.scale = scale
        sticker.rotation = rotation
        onUpdate(sticker)
    }

    // Gesture modifier: transform gestures (pan/zoom/rotate) + long-press delete
    // Using two pointerInput blocks is fine; keeps code simple and reliable.
    val gestureModifier = Modifier
        .pointerInput(Unit) {
            detectTransformGestures { centroid, pan, zoom, rotate ->
                // convert pan from pixels -> dp
                val dx = with(density) { pan.x.toDp().value }
                val dy = with(density) { pan.y.toDp().value }

                offsetX += dx
                offsetY += dy

                // apply zoom and rotation clamping
                scale = (scale * zoom).coerceIn(0.5f, 3.5f)
                rotation += rotate
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { onDelete(sticker) }
            )
        }

    // Render image sticker (drawable) or text/emoji sticker
    if (sticker.drawableResId != null) {
        Image(
            painter = painterResource(sticker.drawableResId),
            contentDescription = null,
            modifier = Modifier
                .offset(x = offsetX.dp, y = offsetY.dp)
                .size((96 * scale).dp)
                .graphicsLayer { rotationZ = rotation }
                .then(gestureModifier)
        )
        return
    }

    if (!sticker.text.isNullOrEmpty()) {
        Text(
            text = sticker.text,
            fontSize = (40 * scale).sp,
            modifier = Modifier
                .offset(x = offsetX.dp, y = offsetY.dp)
                .graphicsLayer { rotationZ = rotation }
                .then(gestureModifier)
        )
    }
}
