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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eventreminder.cards.CardViewModel
import com.example.eventreminder.cards.model.CardData
import com.example.eventreminder.cards.model.CardSticker
import com.example.eventreminder.cards.model.EventKind
import kotlinx.coroutines.launch
import timber.log.Timber

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardPreview"

// =============================================================
// Public API: CardPreview
// - selects template by EventKind
// - exposes onAvatarClick to open picker
// =============================================================
@Composable
fun CardPreview(
    cardData: CardData,
    modifier: Modifier = Modifier,
    vm: CardViewModel,
    onAvatarClick: () -> Unit
) {
    Timber.tag(TAG).d("CardPreview render id=%d kind=%s", cardData.reminderId, cardData.eventKind)

    when (cardData.eventKind) {
        EventKind.BIRTHDAY -> BirthdayCard(cardData, modifier, vm, onAvatarClick)
        EventKind.ANNIVERSARY -> AnniversaryCard(cardData, modifier, vm, onAvatarClick)
        else -> GenericCard(cardData, modifier, vm, onAvatarClick)
    }
}

// =============================================================
// AvatarDraggable
// - single-avatar draggable/scalable circle that uses ViewModel persisted transform
// =============================================================
@Composable
private fun AvatarDraggable(
    avatarBitmap: android.graphics.Bitmap?,
    vm: CardViewModel,
    onClick: () -> Unit
) {
    val xDp by vm.avatarOffsetX.collectAsState()
    val yDp by vm.avatarOffsetY.collectAsState()
    val scale by vm.avatarScale.collectAsState()
    val rotation by vm.avatarRotation.collectAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    var localX by remember { mutableStateOf(xDp) }
    var localY by remember { mutableStateOf(yDp) }
    var localScale by remember { mutableStateOf(scale) }
    var localRot by remember { mutableStateOf(rotation) }

    LaunchedEffect(localX, localY, localScale, localRot) {
        vm.updateAvatarTransform(localX, localY, localScale, localRot)
    }

    Box(
        modifier = Modifier
            .offset(localX.dp, localY.dp)
            .size((72 * localScale).dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.6f))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, rot ->
                    val dx = with(density) { pan.x.toDp().value }
                    val dy = with(density) { pan.y.toDp().value }
                    localX += dx
                    localY += dy
                    localScale = (localScale * zoom).coerceIn(0.4f, 3f)
                    localRot += rot
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap.asImageBitmap(),
                contentDescription = "avatar",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = localRot }
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
// Birthday / Anniversary / Generic templates
// - templates are intentionally simple and match your layout:
//   Title at top, age circle at left-center, birthdate + name at bottom row
// =============================================================
@Composable
private fun BirthdayCard(cardData: CardData, modifier: Modifier, vm: CardViewModel, onAvatarClick: () -> Unit) {
    val avatarBmp by vm.avatarBitmap.collectAsState()
    Surface(
        modifier = modifier
            .width(360.dp)
            .height(240.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = Color.Transparent
    ) {
        Box(Modifier.fillMaxSize()) {
            // Avatar (top-right by default): adjust vm offsets to position
            AvatarDraggable(avatarBitmap = avatarBmp, vm = vm, onClick = onAvatarClick)

            Column(modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 14.dp)) {
                //Text(text = "", style = MaterialTheme.typography.headlineSmall)
                Text(text = "Happy ${cardData.title}", style = MaterialTheme.typography.headlineSmall)
                cardData.name?.let {
                    Spacer(Modifier.height(6.dp))
                    //Text(text = "", style = MaterialTheme.typography.titleMedium)
                    Text(text = it, style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(8.dp))

                // Age circle left-center
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(brush = Brush.radialGradient(colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = cardData.ageOrYearsLabel ?: "-", style = MaterialTheme.typography.headlineSmall)
                }

                Spacer(Modifier.height(60.dp))
                //Spacer(Modifier.height(55.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = cardData.originalDateLabel, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text("Yogesh", style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }

            // Render stickers (z-ordered above)
            RenderStickers(stickers = cardData.stickers, onDelete = { vm.removeSticker(it) }, onUpdate = { vm.updateSticker(it) })
        }
    }
}

@Composable
private fun AnniversaryCard(cardData: CardData, modifier: Modifier, vm: CardViewModel, onAvatarClick: () -> Unit) {
    val avatarBmp by vm.avatarBitmap.collectAsState()
    Surface(modifier = modifier.width(360.dp).height(240.dp).clip(RoundedCornerShape(16.dp)), color = Color.Transparent) {
        Box(Modifier.fillMaxSize()) {
            AvatarDraggable(avatarBmp, vm, onAvatarClick)

            Column(modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 14.dp)) {
                Text(text = "Happy ${cardData.title}", style = MaterialTheme.typography.headlineSmall)
                cardData.name?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(text = it, style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(8.dp))

                // Age circle left-center
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(brush = Brush.radialGradient(colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = cardData.ageOrYearsLabel ?: "-", style = MaterialTheme.typography.headlineSmall)
                }

                Spacer(Modifier.height(34.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = cardData.originalDateLabel, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text("Yogesh", style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }

            RenderStickers(stickers = cardData.stickers, onDelete = { vm.removeSticker(it) }, onUpdate = { vm.updateSticker(it) })
        }
    }
}

@Composable
private fun GenericCard(cardData: CardData, modifier: Modifier, vm: CardViewModel, onAvatarClick: () -> Unit) {
    val avatarBmp by vm.avatarBitmap.collectAsState()
    Surface(modifier = modifier.width(360.dp).height(240.dp).clip(RoundedCornerShape(12.dp)), color = Color.Transparent) {
        Box(Modifier.fillMaxSize()) {
            AvatarDraggable(avatarBmp, vm, onAvatarClick)

            Column(modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 14.dp)) {
                Text(text = cardData.title, style = MaterialTheme.typography.headlineSmall)
                cardData.name?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(text = it, style = MaterialTheme.typography.titleMedium)
                }

                Spacer(Modifier.height(8.dp))

                // Age circle left-center
                /*Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(brush = Brush.radialGradient(colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))))
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = cardData.ageOrYearsLabel ?: "-", style = MaterialTheme.typography.headlineSmall)
                }*/

                Spacer(Modifier.height(104.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = cardData.originalDateLabel, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Text("Yogesh", style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
            }

            RenderStickers(stickers = cardData.stickers, onDelete = { vm.removeSticker(it) }, onUpdate = { vm.updateSticker(it) })
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
