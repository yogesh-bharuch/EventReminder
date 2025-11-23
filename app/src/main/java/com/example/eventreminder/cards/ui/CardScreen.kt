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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventreminder.cards.CardViewModel
import com.example.eventreminder.cards.capture.CaptureBox
import com.example.eventreminder.cards.capture.CaptureController
import com.example.eventreminder.cards.capture.CardShareHelper
import com.example.eventreminder.cards.model.BackgroundItem
import com.example.eventreminder.cards.model.BackgroundPacks
import com.example.eventreminder.cards.model.EmojiStickerPack
import com.example.eventreminder.cards.model.StickerItem
import com.example.eventreminder.cards.model.StickerPacks
import com.example.eventreminder.cards.state.CardUiState
import com.example.eventreminder.cards.util.ImageUtil
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardScreen"

// =============================================================
// PendingAction
// =============================================================
private enum class PendingAction { NONE, SAVE, SHARE }

// =============================================================
// MAIN COMPOSABLE — CardScreen
// =============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(
    reminderId: Long,
    viewModel: CardViewModel = hiltViewModel()
) {
    Timber.tag(TAG).d("CardScreen → reminderId=%d", reminderId)

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val appContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()

    val captureController = remember { CaptureController() }

    var selectedCategory by remember { mutableStateOf(0) }
    val activeStickers: List<StickerItem> = when (selectedCategory) {
        0 -> StickerPacks.birthdayPack              // OR images pack if you prefer
        1 -> EmojiStickerPack.smileys
        2 -> EmojiStickerPack.hearts
        3 -> EmojiStickerPack.celebration
        else -> EmojiStickerPack.misc
    }


    var latestBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingAction by remember { mutableStateOf(PendingAction.NONE) }

    // Cropper states
    var showCropper by remember { mutableStateOf(false) }
    var bitmapForCrop by remember { mutableStateOf<Bitmap?>(null) }


    // Avatar picker
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            Timber.tag(TAG).d("Avatar picked → $uri")
            val bmp = ImageUtil.loadBitmapFromUri(context, uri, maxDim = 1600)
            if (bmp != null) {
                bitmapForCrop = bmp
                showCropper = true
            }
        }
    }

    // Background picker
    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            Timber.tag(TAG).d("Background picked → $uri")
            viewModel.onBackgroundImageSelected(context, uri)
        }
    }

    // =========================================================
    // Scaffold
    // =========================================================
    Scaffold(
        topBar = { TopAppBar(title = { Text("Event Card") }) }
    ) { paddingValues ->

        Box(modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(modifier = Modifier.height(20.dp))

                // =====================================================
                // CARD AREA
                // =====================================================
                when (uiState) {
                    is CardUiState.Loading -> CircularProgressIndicator()
                    is CardUiState.Error -> Text((uiState as CardUiState.Error).message)
                    is CardUiState.Placeholder -> Text("No reminderId provided.")
                    is CardUiState.Data -> {

                        val cardData = (uiState as CardUiState.Data).cardData

                        CaptureBox(
                            controller = captureController,
                            onCaptured = { bmp ->
                                Timber.tag(TAG).d("Captured bitmap %dx%d", bmp.width, bmp.height)
                                latestBitmap = bmp
                            }
                        ) {

                            val bgBmp by viewModel.backgroundBitmap.collectAsState()

                            Box(
                                modifier = Modifier
                                    .widthIn(max = 420.dp)
                                    .wrapContentHeight(),
                                contentAlignment = Alignment.TopCenter
                            ) {

                                // blank surface fallback
                                Surface(
                                    modifier = Modifier
                                        .width(360.dp)
                                        .height(240.dp)
                                        .shadow(4.dp, RoundedCornerShape(16.dp))
                                        .clip(RoundedCornerShape(16.dp)),
                                    color = if (bgBmp == null)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                    else
                                        androidx.compose.ui.graphics.Color.Transparent
                                ) {}

                                // Background
                                if (bgBmp != null) {
                                    Image(
                                        bitmap = bgBmp!!.asImageBitmap(),
                                        contentDescription = "background",
                                        modifier = Modifier
                                            .width(360.dp)
                                            .height(240.dp)
                                            .clip(RoundedCornerShape(16.dp)),
                                        contentScale = ContentScale.Crop
                                    )

                                    // Dim overlay
                                    Box(
                                        modifier = Modifier
                                            .width(360.dp)
                                            .height(240.dp)
                                            .background(
                                                androidx.compose.ui.graphics.Color.Black
                                                    .copy(alpha = 0.18f)
                                            )
                                            .clip(RoundedCornerShape(16.dp))
                                    )
                                }

                                // Foreground template (CardPreview UI)
                                CardPreview(
                                    cardData = cardData,
                                    modifier = Modifier
                                        .width(360.dp)
                                        .height(240.dp),
                                    vm = viewModel,
                                    onAvatarClick = {
                                        avatarPicker.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // =====================================================
                        // COMBINED CONTROLS (background + sticker categories)
                        // =====================================================
                        CombinedControls(
                            onPickBackground = {
                                backgroundPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onClearBackground = { viewModel.clearBackground() },

                            backgroundItems = BackgroundPacks.defaultPack,
                            onPredefinedBgSelected = { bgItem ->

                                val safeContext = appContext

                                coroutineScope.launch {
                                    try {
                                        val bmp = BitmapFactory.decodeResource(
                                            safeContext.resources,
                                            bgItem.resId
                                        )
                                        val path = ImageUtil.saveBitmapToCache(
                                            safeContext,
                                            bmp,
                                            filenamePrefix = "bg_"
                                        )
                                        if (path != null) {
                                            viewModel.onBackgroundImageSelected(
                                                safeContext,
                                                Uri.fromFile(File(path))
                                            )
                                        }
                                    } catch (t: Throwable) {
                                        Timber.tag(TAG).e(t, "Failed preload BG")
                                    }
                                }
                            },

                            selectedCategory = selectedCategory,
                            onCategorySelected = { selectedCategory = it },

                            stickerItems = activeStickers,
                            onStickerClick = { viewModel.addSticker(it) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ---------------------------------------------------------
                        // TOGGLES: Show/Hide Title + Name
                        // ---------------------------------------------------------
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            val showTitle by viewModel.showTitle.collectAsState()
                            val showName by viewModel.showName.collectAsState()

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = showTitle,
                                    onCheckedChange = { viewModel.toggleShowTitle(it) }
                                )
                                Text("Show Title")
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = showName,
                                    onCheckedChange = { viewModel.toggleShowName(it) }
                                )
                                Text("Show Name")
                            }
                        }

                        // =====================================================
                        // SAVE / SHARE
                        // =====================================================
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

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

    // =============================================================
    // HANDLE SAVE/SHARE EXPORT
    // =============================================================
    LaunchedEffect(latestBitmap) {
        val bmp = latestBitmap ?: return@LaunchedEffect

        when (pendingAction) {
            PendingAction.SAVE -> CardShareHelper.saveCard(appContext, bmp)
            PendingAction.SHARE -> CardShareHelper.shareCard(appContext, bmp)
            else -> Unit
        }

        pendingAction = PendingAction.NONE
        latestBitmap = null
    }

    // =============================================================
    // AVATAR CROPPER OVERLAY
    // =============================================================
    if (showCropper && bitmapForCrop != null) {
        CropperOverlay(
            sourceBitmap = bitmapForCrop!!,
            onCancel = {
                showCropper = false
                bitmapForCrop = null
            },
            onConfirm = { cropped ->
                viewModel.onCroppedSquareBitmapSaved(appContext, cropped)
                showCropper = false
                bitmapForCrop = null
            }
        )
    }
}
