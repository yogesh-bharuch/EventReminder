package com.example.eventreminder.cards.ui

// =============================================================
// Imports
// =============================================================
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventreminder.cards.CardViewModel
import com.example.eventreminder.cards.capture.CaptureBox
import com.example.eventreminder.cards.capture.CaptureController
import com.example.eventreminder.cards.capture.CardShareHelper
import com.example.eventreminder.cards.model.StickerPacks
import com.example.eventreminder.cards.state.CardUiState
import com.example.eventreminder.cards.util.ImageUtil
import timber.log.Timber

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
// CardScreen — FIXED, CLEAN, ERROR-FREE VERSION
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

    val captureController = remember { CaptureController() }

    var latestBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingAction by remember { mutableStateOf(PendingAction.NONE) }

    // Crop state
    var showCropper by remember { mutableStateOf(false) }
    var bitmapForCrop by remember { mutableStateOf<Bitmap?>(null) }

    // ---------------------------------------------------------
    // Image Picker Launcher
    // ---------------------------------------------------------
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            Timber.tag(TAG).d("Image picked: $uri")

            // Load scaled bitmap for crop overlay
            val bmp = ImageUtil.loadBitmapFromUri(context, uri)
            if (bmp != null) {
                bitmapForCrop = bmp
                showCropper = true
            }
        }
    }

    // ---------------------------------------------------------
    // Scaffold
    // ---------------------------------------------------------
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Event Card") })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
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

                is CardUiState.Loading ->
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )

                is CardUiState.Error ->
                    Text(
                        text = (uiState as CardUiState.Error).message,
                        modifier = Modifier.align(Alignment.Center)
                    )

                is CardUiState.Placeholder ->
                    Text(
                        "No reminderId provided.",
                        modifier = Modifier.align(Alignment.Center)
                    )

                is CardUiState.Data -> {
                    val cardData = (uiState as CardUiState.Data).cardData

                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

                        // ---------------------------------------------------------
                        // CARD CAPTURE BOX
                        // ---------------------------------------------------------
                        CaptureBox(
                            controller = captureController,
                            onCaptured = { bmp ->
                                Timber.tag(TAG).d("Card captured: ${bmp.width}x${bmp.height}")
                                latestBitmap = bmp
                            }
                        ) {
                            // Pass avatar click callback down to template
                            CardPreview(
                                cardData = cardData,
                                modifier = Modifier.fillMaxWidth(),
                                vm = viewModel,
                                onAvatarClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                            )
                        }

                        // Stickers
                        StickerBar(
                            items = StickerPacks.birthdayPack.items,
                            onStickerClick = { viewModel.addSticker(it) }
                        )

                        // ---------------------------------------------------------
                        // Action Buttons
                        // ---------------------------------------------------------
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
    // Show cropper overlay
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
// Cropper Overlay — Lightweight, simple center-square crop
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
