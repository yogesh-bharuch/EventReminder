package com.example.eventreminder.cards.pixelcanvas.ui

// =============================================================
// CardEditorScreen.kt — MAIN EDITOR SCREEN (UUID-Compatible)
// =============================================================

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventreminder.cards.CardUiState
import com.example.eventreminder.cards.CardViewModel
import com.example.eventreminder.cards.pixelcanvas.AvatarTransformPx
import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import com.example.eventreminder.cards.pixelcanvas.editor.GestureHandlers
import com.example.eventreminder.cards.pixelcanvas.export.CardExportUtils
import com.example.eventreminder.cards.pixelcanvas.export.SafStorageHelper
import com.example.eventreminder.cards.pixelcanvas.panels.SaveShareRow
import com.example.eventreminder.cards.pixelcanvas.pickers.AvatarPickerRow
import com.example.eventreminder.cards.pixelcanvas.pickers.BackgroundPickerRow
import com.example.eventreminder.cards.pixelcanvas.pickers.CardExpandableColorPickerRow
import com.example.eventreminder.cards.pixelcanvas.stickers.catalog.StickerCatalogPacks
import com.example.eventreminder.cards.pixelcanvas.stickers.model.StickerCategory
import com.example.eventreminder.cards.pixelcanvas.stickers.model.StickerPx
import com.example.eventreminder.cards.pixelcanvas.stickers.panel.StickerCategoryBar
import com.example.eventreminder.cards.pixelcanvas.stickers.panel.StickerListPanel
import com.example.eventreminder.cards.util.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TAG = "PixelCardPreviewScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditorScreen(reminderId: String) {   // ⭐ now UUID String

    Timber.tag(TAG).d("CardEditorScreen Loaded id=%s", reminderId)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---------------------------------------------------------
    // ViewModel
    // ---------------------------------------------------------
    val viewModel: CardViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    // Load using UUID
    LaunchedEffect(reminderId) {
        viewModel.forceLoadReminder(reminderId)
    }

    // VM-driven states
    val vmStickers by viewModel.pixelStickers.collectAsState()
    val activeStickerId by viewModel.activeStickerId.collectAsState()
    val vmAvatarBitmap by viewModel.pixelAvatarBitmap.collectAsState()

    val xNorm by viewModel.pixelAvatarXNorm.collectAsState()
    val yNorm by viewModel.pixelAvatarYNorm.collectAsState()
    val scale by viewModel.pixelAvatarScale.collectAsState()
    val rotation by viewModel.pixelAvatarRotationDeg.collectAsState()

    val vmTransform = AvatarTransformPx(xNorm, yNorm, scale, rotation)

    // Card dimensions
    val spec = remember { CardSpecPx.default1080x1200() }

    // ---------------------------------------------------------
    // DEFAULT canvas model
    // ---------------------------------------------------------
    var cardData by remember {
        mutableStateOf(
            CardDataPx(
                reminderId = reminderId,     // ⭐ UUID propagated
                titleText = "Happy Birthday",
                nameText = "Name",
                showTitle = true,
                showName = true,
                avatarBitmap = null,
                avatarTransform = AvatarTransformPx(),
                backgroundBitmap = null,
                stickers = emptyList(),
                originalDateLabel = "",
                nextDateLabel = "",
                ageOrYearsLabel = null
            )
        )
    }

    // ---------------------------------------------------------
    // SYNC ViewModel → Canvas Model
    // ---------------------------------------------------------
    LaunchedEffect(vmAvatarBitmap, vmTransform, vmStickers, activeStickerId, uiState) {

        // Sticker mapping
        val mappedStickers = vmStickers.map { s ->
            StickerPx(
                id = s.id,
                drawableResId = s.drawableResId,
                bitmap = s.bitmap,
                text = s.text,
                xNorm = s.xNorm,
                yNorm = s.yNorm,
                scale = s.scale,
                rotationDeg = s.rotationDeg
            )
        }

        // Sync title/name/date
        if (uiState is CardUiState.Data) {
            val cd = (uiState as CardUiState.Data).cardData
            cardData = cardData.copy(
                reminderId = cd.reminderId,      // ⭐ correct UUID
                titleText = cd.title,
                nameText = cd.name,
                originalDateLabel = cd.originalDateLabel,
                nextDateLabel = cd.nextDateLabel,
                ageOrYearsLabel = cd.ageOrYearsLabel
            )
        }

        // Sync avatar + stickers
        cardData = cardData.copy(
            avatarBitmap = vmAvatarBitmap,
            avatarTransform = vmTransform,
            stickers = mappedStickers,
            activeStickerId = activeStickerId
        )
    }

    // ---------------------------------------------------------
    // BACKGROUND PICKER
    // ---------------------------------------------------------
    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val bmp = ImageUtil.loadBitmapFromUri(context, it, maxDim = 2000)
            if (bmp != null) cardData = cardData.copy(backgroundBitmap = bmp)
            else Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------
    // SAF Save folder picker
    // ---------------------------------------------------------
    val openTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult

        try {
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)

            scope.launch(Dispatchers.IO) {
                SafStorageHelper.saveTreeUri(context, treeUri.toString())
            }

            Toast.makeText(context, "Folder selected", Toast.LENGTH_SHORT).show()

        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Persist tree failed")
        }
    }

    fun onSaveClicked() {
        scope.launch {
            val savedTree = SafStorageHelper.getTreeUriFlow(context).first()
            if (savedTree == null) {
                openTreeLauncher.launch(null)
                return@launch
            }
            CardExportUtils.savePngToSaf(context, spec, cardData)
        }
    }

    fun onShareClicked() {
        scope.launch {
            val uri = CardExportUtils.sharePng(context, spec, cardData)
            uri?.let {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, it)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Card"))
            }
        }
    }

    // ---------------------------------------------------------
    // Gestures
    // ---------------------------------------------------------
    val boxSizeState = remember { mutableStateOf(IntSize.Zero) }

    val gestureModifier = GestureHandlers.modifier(
        spec = spec,
        boxSize = boxSizeState.value,
        cardData = cardData,
        stickerList = vmStickers,
        activeStickerId = activeStickerId,
        viewModel = viewModel
    )

    // ---------------------------------------------------------
    // UI Layout
    // ---------------------------------------------------------
    Scaffold(
        topBar = { TopAppBar(title = { Text("Create Event Card") }) }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF5F5F5)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            item {
                Spacer(Modifier.height(4.dp))
                Text("Design your card (BG + Photo + Stickers)", modifier = Modifier.padding(8.dp))
                Spacer(Modifier.height(16.dp))
            }

            // TITLE COLOR
            item {
                CardExpandableColorPickerRow(
                    label = "Title Color",
                    selectedColor = cardData.titleColor,
                    onColorSelected = { newColor -> cardData = cardData.copy(titleColor = newColor) }
                )
                Spacer(Modifier.height(4.dp))
            }

            // NAME COLOR
            item {
                CardExpandableColorPickerRow(
                    label = "Name Color",
                    selectedColor = cardData.nameColor,
                    onColorSelected = { newColor -> cardData = cardData.copy(nameColor = newColor) }
                )
                Spacer(Modifier.height(4.dp))
            }

            // DATE COLOR
            item {
                CardExpandableColorPickerRow(
                    label = "Date Color",
                    selectedColor = cardData.originalDateColor,
                    onColorSelected = { newColor -> cardData = cardData.copy(originalDateColor = newColor) }
                )
                Spacer(Modifier.height(12.dp))
            }

            // CANVAS AREA
            item {
                CardCanvasArea(
                    spec = spec,
                    cardData = cardData,
                    boxSizeState = boxSizeState,
                    gestureModifier = gestureModifier,
                    onDeleteSticker = { id -> viewModel.removeSticker(id) }
                )
                Spacer(Modifier.height(32.dp))
            }

            // BACKGROUND
            item {
                BackgroundPickerRow(
                    cardData = cardData,
                    onBackgroundChanged = { bmp -> cardData = cardData.copy(backgroundBitmap = bmp) },
                    onPickBackground = {
                        backgroundPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            // AVATAR
            item {
                AvatarPickerRow(viewModel = viewModel)
                Spacer(Modifier.height(8.dp))
            }

            // SAVE & SHARE
            item {
                SaveShareRow(onSaveClicked = ::onSaveClicked, onShareClicked = ::onShareClicked)
                Spacer(Modifier.height(8.dp))
            }

            // STICKERS
            item {
                var selectedCategory by remember { mutableStateOf<StickerCategory?>(null) }

                val categories = listOf(
                    StickerCategory.Smileys,
                    StickerCategory.Hearts,
                    StickerCategory.Celebration,
                    StickerCategory.Misc
                )

                StickerCategoryBar(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelect = { selectedCategory = it }
                )

                Spacer(Modifier.height(8.dp))

                selectedCategory?.let { cat ->
                    StickerListPanel(
                        stickers = StickerCatalogPacks.getPack(cat),
                        onStickerSelected = { item -> viewModel.addStickerFromCatalog(item) }
                    )
                }
            }
        }
    }
}

private fun Float.coerceFinite(): Float =
    if (this.isFinite()) this else 1f
