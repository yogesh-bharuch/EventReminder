package com.example.eventreminder.cards.ui

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.eventreminder.cards.model.CardSticker
import timber.log.Timber

// =============================================================
// DraggableSticker composable (separated file for clarity)
// - supports image and text stickers, pan/zoom/rotate and long-press delete
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
    var rotation by remember { mutableStateOf(sticker.rotation) }

    // push updates back to caller
    LaunchedEffect(offsetX, offsetY, scale, rotation) {
        sticker.x = offsetX
        sticker.y = offsetY
        sticker.scale = scale
        sticker.rotation = rotation
        onUpdate(sticker)
    }

    val gestureMod = Modifier
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, rot ->
                val dx = with(density) { pan.x.toDp().value }
                val dy = with(density) { pan.y.toDp().value }
                offsetX += dx
                offsetY += dy
                scale = (scale * zoom).coerceIn(0.5f, 3.5f)
                rotation += rot
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(onLongPress = {
                Timber.tag("DraggableSticker").d("Long pressed → delete sticker id=%s", sticker.id)
                onDelete(sticker)
            })
        }

    if (sticker.drawableResId != null) {
        Image(
            painter = painterResource(sticker.drawableResId),
            contentDescription = sticker.text ?: "",
            modifier = Modifier
                .offset(x = offsetX.dp, y = offsetY.dp)
                .size((96 * scale).dp)
                .graphicsLayer { rotationZ = rotation }
                .then(gestureMod)
        )
        return
    }

    // text/emoji fallback
    if (!sticker.text.isNullOrEmpty()) {
        Text(
            text = sticker.text!!,
            fontSize = (40 * scale).sp,
            modifier = Modifier
                .offset(x = offsetX.dp, y = offsetY.dp)
                .graphicsLayer { rotationZ = rotation }
                .then(gestureMod)
        )
    }
}
