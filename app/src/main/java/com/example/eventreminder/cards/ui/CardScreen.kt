package com.example.eventreminder.cards.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventreminder.cards.CardViewModel
import com.example.eventreminder.cards.capture.CaptureBox
import com.example.eventreminder.cards.capture.CaptureController
import com.example.eventreminder.cards.capture.CardShareHelper
import com.example.eventreminder.cards.model.StickerPacks
import com.example.eventreminder.cards.model.BackgroundPacks
import com.example.eventreminder.cards.model.BackgroundItem
import com.example.eventreminder.cards.state.CardUiState
import com.example.eventreminder.cards.util.ImageUtil
import timber.log.Timber
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import java.io.File

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardScreen"

// =============================================================
// Enum for Save/Share pending actions
// =============================================================
private enum class PendingAction {
    NONE, SAVE, SHARE
}

// =============================================================
// CardScreen — Background Pack (Option A) + Style 3 (overlay dim)
// =============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(
    reminderId: Long,
    viewModel: CardViewModel = hiltViewModel()
) {
    Timber.tag(TAG).d("CardScreen → reminderId=$reminderId")

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val captureController = remember { CaptureController() }

    var latestBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingAction by remember { mutableStateOf(PendingAction.NONE) }

    // Crop state (avatar)
    var showCropper by remember { mutableStateOf(false) }
    var bitmapForCrop by remember { mutableStateOf<Bitmap?>(null) }

    // Image picker for avatar (re-used)
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            Timber.tag(TAG).d("Image picked: $uri")
            val bmp = ImageUtil.loadBitmapFromUri(context, uri)
            if (bmp != null) {
                bitmapForCrop = bmp
                showCropper = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Event Card") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // For avatar: open picker
                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) {
                Text("Pick")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            when (uiState) {
                is CardUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is CardUiState.Error -> Text(text = (uiState as CardUiState.Error).message, modifier = Modifier.align(Alignment.Center))
                is CardUiState.Placeholder -> Text("No reminderId provided.", modifier = Modifier.align(Alignment.Center))
                is CardUiState.Data -> {
                    val cardData = (uiState as CardUiState.Data).cardData

                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {

                        // ---------------------------------------------------------
                        // CARD CAPTURE BOX (shows background under card with overlay)
                        // ---------------------------------------------------------
                        CaptureBox(
                            controller = captureController,
                            onCaptured = { bmp ->
                                Timber.tag(TAG).d("Card captured: ${bmp.width}x${bmp.height}")
                                latestBitmap = bmp
                            }
                        ) {
                            // Compose a card container that renders background (persisted)
                            val bgBmp by viewModel.backgroundBitmap.collectAsState()
                            Box(modifier = Modifier.fillMaxWidth()) {

                                // Card visual size should match CardPreview's internal sizes
                                val cardWidth = Modifier
                                    .fillMaxWidth()
                                val cardHeightDp = 240.dp

                                // Background (if any)
                                if (bgBmp != null) {
                                    Image(
                                        bitmap = bgBmp!!.asImageBitmap(),
                                        contentDescription = "background",
                                        modifier = Modifier
                                            .height(cardHeightDp)
                                            .then(cardWidth)
                                            .clip(RoundedCornerShape(16.dp)),
                                        contentScale = ContentScale.Crop
                                    )

                                    // Semi-transparent overlay (Style 3)
                                    Box(
                                        modifier = Modifier
                                            .height(cardHeightDp)
                                            .then(cardWidth)
                                            .background(Color.Black.copy(alpha = 0.40f))
                                            .clip(RoundedCornerShape(16.dp))
                                    )
                                }

                                // Foreground card content on top of background
                                Box(
                                    modifier = Modifier
                                        .height(cardHeightDp)
                                        .then(cardWidth)
                                ) {
                                    CardPreview(
                                        cardData = cardData,
                                        modifier = Modifier.fillMaxWidth(),
                                        vm = viewModel,
                                        onAvatarClick = {
                                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    )
                                }
                            }
                        }

                        // ---------------------------------------------------------
                        // BackgroundBar (thumbnail pack) — Option A UX
                        // ---------------------------------------------------------
                        BackgroundBar(
                            items = BackgroundPacks.defaultPack,
                            onBackgroundSelected = { bgItem ->
                                // When user taps a thumbnail:
                                // 1) decode resource to Bitmap
                                // 2) save to cache
                                // 3) call viewModel.onBackgroundImageSelected with a Uri pointing to the cached file
                                coroutineScope.launch {
                                    try {
                                        val resId = bgItem.resId
                                        val bmp = BitmapFactory.decodeResource(context.resources, resId)
                                        if (bmp == null) {
                                            Timber.tag(TAG).w("Failed decode background drawable res=$resId")
                                            return@launch
                                        }
                                        val cachedPath = ImageUtil.saveBitmapToCache(context, bmp, filenamePrefix = "bg_")
                                        if (cachedPath == null) {
                                            Timber.tag(TAG).e("Failed saving background to cache")
                                            return@launch
                                        }
                                        // Persist via existing pipeline (ViewModel)
                                        viewModel.onBackgroundImageSelected(context, Uri.fromFile(File(cachedPath)))
                                    } catch (t: Throwable) {
                                        Timber.tag(TAG).e(t, "Error handling background selection")
                                    }
                                }
                            }
                        )

                        // Stickers
                        StickerBar(
                            items = StickerPacks.birthdayPack,
                            onStickerClick = { viewModel.addSticker(it) }
                        )

                        // Action Buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    pendingAction = PendingAction.SAVE
                                    captureController.capture()
                                }
                            ) { Text("Save") }

                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    pendingAction = PendingAction.SHARE
                                    captureController.capture()
                                }
                            ) { Text("Share") }
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------
    // Handle final exported bitmap (Save/Share)
    // ---------------------------------------------------------
    LaunchedEffect(latestBitmap) {
        val bmp = latestBitmap ?: return@LaunchedEffect

        when (pendingAction) {
            PendingAction.SAVE -> {
                CardShareHelper.saveCard(context, bmp)
            }
            PendingAction.SHARE -> {
                CardShareHelper.shareCard(context, bmp)
            }
            else -> Unit
        }

        pendingAction = PendingAction.NONE
        latestBitmap = null
    }

    // ---------------------------------------------------------
    // Show cropper overlay for avatar
    // ---------------------------------------------------------
    if (showCropper && bitmapForCrop != null) {
        CropperOverlay(
            sourceBitmap = bitmapForCrop!!,
            onCancel = {
                showCropper = false
                bitmapForCrop = null
            },
            onConfirm = { croppedSquare ->
                viewModel.onCroppedSquareBitmapSaved(context, croppedSquare)
                showCropper = false
                bitmapForCrop = null
            }
        )
    }
}

// =============================================================
// BackgroundBar — a horizontal row of thumbnails (pack)
// =============================================================
@Composable
fun BackgroundBar(
    items: List<BackgroundItem>,
    onBackgroundSelected: (BackgroundItem) -> Unit
) {
    Column {
        Text(
            text = "Backgrounds",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 6.dp)
        ) {
            items(items) { item ->
                val painter: Painter = painterResource(item.resId)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(4.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onBackgroundSelected(item) }
                ) {
                    Image(
                        painter = painter,
                        contentDescription = item.name ?: "bg",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

// =============================================================
// Cropper Overlay — Lightweight, simple center-square crop
// (unchanged from previous implementation)
// =============================================================
@Composable
private fun CropperOverlay(
    sourceBitmap: Bitmap,
    onCancel: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    val minScale = 0.5f
    val maxScale = 4f
    val viewportSize = 320.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {

        // Viewport for cropping
        Box(
            modifier = Modifier
                .size(viewportSize)
                .shadow(12.dp, MaterialTheme.shapes.medium)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(minScale, maxScale)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            androidx.compose.foundation.Image(
                bitmap = sourceBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
            )
        }

        // Buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Button(
                onClick = {
                    // Create centered square crop
                    val scaled = Bitmap.createScaledBitmap(
                        sourceBitmap,
                        (sourceBitmap.width * scale).toInt().coerceAtLeast(1),
                        (sourceBitmap.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                    val size = minOf(scaled.width, scaled.height)
                    val left = (scaled.width - size) / 2
                    val top = (scaled.height - size) / 2
                    val cropped = Bitmap.createBitmap(scaled, left, top, size, size)
                    onConfirm(cropped)
                }
            ) {
                Text("Confirm")
            }
        }
    }
}
