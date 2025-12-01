package com.example.eventreminder.cards.pixel

// =============================================================
// PixelCardPreviewScreen.kt — CLEAN + STICKER-READY VERSION
// - Avatar/background/save/share preserved
// - Sticker system integrated with CardViewModel (Step-2)
// - Gesture pipeline ready for Step-3 routing
// =============================================================

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventreminder.cards.CardViewModel
import com.example.eventreminder.cards.pixel.stickers.StickerCatalogItem
import com.example.eventreminder.cards.util.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import com.example.eventreminder.R
import com.example.eventreminder.cards.pixel.stickers.StickerBitmapCache
import com.example.eventreminder.cards.pixel.stickers.StickerCatalogPacks
import com.example.eventreminder.cards.pixel.stickers.StickerCategory
import com.example.eventreminder.cards.pixel.stickers.StickerPreviewItem


private const val TAG = "PixelCardPreviewScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelCardPreviewScreen() {

    Timber.tag(TAG).d("PixelCardPreviewScreen Loaded")

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel: CardViewModel = hiltViewModel()

    // Stickers from VM (LIVE!)
    val vmStickers by viewModel.pixelStickers.collectAsState()
    val stickerList by viewModel.pixelStickers.collectAsState()
    val activeStickerId by viewModel.activeStickerId.collectAsState()


    // Avatar bitmap + transforms
    val vmAvatarBitmap by viewModel.pixelAvatarBitmap.collectAsState()

    val spec = remember { CardSpecPx.default1080x1200() }

    val xNorm by viewModel.pixelAvatarXNorm.collectAsState()
    val yNorm by viewModel.pixelAvatarYNorm.collectAsState()
    val scale by viewModel.pixelAvatarScale.collectAsState()
    val rotation by viewModel.pixelAvatarRotationDeg.collectAsState()

    val vmTransform = AvatarTransformPx(xNorm, yNorm, scale, rotation)

    // -------------------------------
    // CardDataPx (canvas input)
    // -------------------------------
    var cardData by remember {
        mutableStateOf(
            CardDataPx(
                reminderId = 1L,
                titleText = "Happy Birthday",
                nameText = "Yogesh",
                showTitle = true,
                showName = true,
                avatarBitmap = null,
                avatarTransform = AvatarTransformPx(),
                backgroundBitmap = null,
                stickers = emptyList(),
                originalDateLabel = "Jan 1, 1990",
                nextDateLabel = "Fri, Jan 1, 2026",
                ageOrYearsLabel = "34"
            )
        )
    }

    // -------------------------------
    // Sync VM -> CardDataPx
    // -------------------------------

    LaunchedEffect(vmAvatarBitmap, vmTransform, vmStickers) {

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

        cardData = cardData.copy(
            avatarBitmap = vmAvatarBitmap,
            avatarTransform = vmTransform,
            stickers = mappedStickers
        )
    }

    // -------------------------------
    // Background picker
    // -------------------------------
    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bmp = ImageUtil.loadBitmapFromUri(context, uri, maxDim = 2000)
            if (bmp != null) cardData = cardData.copy(backgroundBitmap = bmp)
            else Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------------
    // SAF Save
    // -------------------------------
    val openTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)

            scope.launch(Dispatchers.IO) {
                SafStorageHelper.saveTreeUri(context, treeUri.toString())
            }
            Toast.makeText(context, "Folder selected", Toast.LENGTH_SHORT).show()

        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "persist tree failed")
        }
    }

    suspend fun ensureEventFolder(treeUri: Uri): DocumentFile? {
        return try {
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            val existing = treeDoc.findFile("EventReminderCards")
            if (existing != null && existing.isDirectory) return existing
            treeDoc.createDirectory("EventReminderCards")
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "ensureEventFolder failed")
            null
        }
    }

    suspend fun savePngViaSaf(): Uri? {
        return try {
            val savedUri: Uri? = SafStorageHelper.getTreeUriFlow(context).first()
            if (savedUri == null) return null

            val folder = ensureEventFolder(savedUri) ?: return null
            val filename = "Card_${System.currentTimeMillis()}.png"
            val newFile = folder.createFile("image/png", filename) ?: return null

            val out: OutputStream? = context.contentResolver.openOutputStream(newFile.uri)
            if (out == null) return null

            val bmp = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            PixelRenderer.renderToAndroidCanvas(canvas, spec, cardData)

            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()

            newFile.uri
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "savePngViaSaf failed")
            null
        }
    }

    fun onSaveClicked() {
        scope.launch {
            val savedUri: Uri? = SafStorageHelper.getTreeUriFlow(context).first()
            if (savedUri != null) {
                savePngViaSaf()
                return@launch
            }
            openTreeLauncher.launch(null)
        }
    }

    // -------------------------------
    // Share
    // -------------------------------
    fun onShareClicked() {
        scope.launch(Dispatchers.IO) {
            val bmp = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            PixelRenderer.renderToAndroidCanvas(canvas, spec, cardData)

            val cacheFile = File(context.cacheDir, "share_${System.currentTimeMillis()}.png")
            FileOutputStream(cacheFile).use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)

            scope.launch {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Card PNG"))
            }
        }
    }

    // -------------------------------
    // Gesture Layer (Step-3 Ready)
    // -------------------------------
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val gestureModifier = Modifier.pointerInput(
        spec, boxSize, stickerList, activeStickerId
    ) {
        awaitPointerEventScope {

            while (true) {

                var event = awaitPointerEvent()
                val first = event.changes.firstOrNull { it.pressed } ?: continue

                val tx = first.position.x
                val ty = first.position.y

                // ------------------------------------------------------
                // 1) STICKERS FIRST (topmost)
                // ------------------------------------------------------
                val touchedSticker = PixelRenderer.findTopmostStickerUnderTouch(
                    tx, ty, spec, cardData
                )

                if (touchedSticker != null) {

                    viewModel.setActiveSticker(touchedSticker.id)

                    // ---> STICKER GESTURE LOOP (NO LONG PRESS)
                    while (event.changes.any { it.pressed }) {

                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom().coerceFinite()
                        val rot = event.calculateRotation().coerceFinite()

                        viewModel.updateActiveStickerPosition(
                            pan.x / boxSize.width.toFloat(),
                            pan.y / boxSize.height.toFloat()
                        )
                        viewModel.updateActiveStickerScale(zoom)
                        viewModel.updateActiveStickerRotation(rot)

                        event.changes.forEach { it.consume() }
                        event = awaitPointerEvent()
                    }

                    continue
                }

                // ------------------------------------------------------
                // 2) AVATAR SECOND
                // ------------------------------------------------------
                val hitAvatar = PixelRenderer.isTouchInsideAvatar(tx, ty, spec, cardData)

                if (!hitAvatar) {
                    viewModel.setActiveSticker(null)

                    while (event.changes.any { it.pressed }) {
                        event = awaitPointerEvent()
                    }
                    continue
                }

                // ---> AVATAR GESTURE LOOP
                while (event.changes.any { it.pressed }) {

                    val pan = event.calculatePan()
                    val zoom = event.calculateZoom().coerceFinite()
                    val rot = event.calculateRotation().coerceFinite()

                    viewModel.updatePixelAvatarPosition(
                        pan.x / boxSize.width.toFloat(),
                        pan.y / boxSize.height.toFloat()
                    )
                    viewModel.updatePixelAvatarScale(zoom)
                    viewModel.updatePixelAvatarRotation(rot)

                    event.changes.forEach { it.consume() }
                    event = awaitPointerEvent()
                }
            }
        }
    }


    // -------------------------------
    // UI
    // -------------------------------
    Scaffold(
        topBar = { TopAppBar(title = { Text("Pixel Renderer") }) }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF5F5F5)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // header Design your card
            item {
                Spacer(Modifier.height(12.dp))
                Text("Design your card (BG + Photo + Stickers)")
                Spacer(Modifier.height(16.dp))
            }

            // Canvas + Sticker DELETE BUTTON
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .aspectRatio(1080f / 1200f)
                        .background(Color.LightGray)
                        .onGloballyPositioned {
                            boxSize = IntSize(it.size.width, it.size.height)
                        }
                        .then(gestureModifier)
                ) {
                    PixelCanvas(
                        spec = spec,
                        data = cardData,
                        modifier = Modifier.fillMaxSize()
                    )

                    val density = LocalDensity.current

                    // --- DELETE BUTTON OVERLAY ---
                    if (activeStickerId != null) {
                        DeleteStickerButtonOverlay(
                            spec = spec,
                            boxSize = boxSize,
                            cardData = cardData,
                            activeStickerId = activeStickerId,
                            onDelete = { viewModel.removeSticker(it) }
                        )
                    }

                }
                Spacer(Modifier.height(16.dp))
            }

            // Background controls
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        backgroundPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) { Text("Pick Bg") }

                    Button(onClick = { cardData = cardData.copy(backgroundBitmap = null) }) {
                        Text("Clear Bg")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Save/Share
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { onSaveClicked() }) { Text("Save PNG") }
                    Button(onClick = { onShareClicked() }) { Text("Share PNG") }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Avatar controls
            item {
                val avatarPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()
                ) { uri ->
                    if (uri != null) viewModel.onPixelAvatarImageSelected(context, uri)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = {
                        avatarPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Text("Pick Photo")
                    }
                    Button(onClick = { viewModel.clearPixelAvatar() }) {
                        Text("Clear Photo")
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            // ------------------------------------------------------
            // CATEGORY BUTTONS  +  STICKER LIST (Expandable)
            // ------------------------------------------------------
            item {

                // Stores CURRENT selected category
                var selectedCategory by remember { mutableStateOf<StickerCategory?>(null) }

                // -------------------------
                // CATEGORY BUTTON ROW
                // -------------------------
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Birthday, Smileys, Hearts....
                    val categories = listOf(
                        //StickerCategory.Birthday,
                        StickerCategory.Smileys,
                        StickerCategory.Hearts,
                        StickerCategory.Celebration,
                        StickerCategory.Misc
                    )

                    // Catagory buttons lazyRow Birthday, Smileys, Hearts....
                    items(categories) { cat ->
                        Button(
                            onClick = {
                                selectedCategory =
                                    if (selectedCategory == cat) null else cat   // toggle
                            },
                        ) {
                            Text(cat.name)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // -------------------------
                // STICKER LIST ROW (only if category is selected)
                // -------------------------
                selectedCategory?.let { cat ->
                    val list = StickerCatalogPacks.getPack(cat)

                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(list) { item ->
                            StickerPreviewItem(
                                item = item,
                                onClick = { viewModel.addStickerFromCatalog(item) }
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }

            /*item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    items(StickerCatalogPacks.birthdayPack) { item ->
                        StickerPreviewItem(item) {
                            viewModel.addStickerFromCatalog(item)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            } */

        }
    }
}

/* --------------------------
   Helpers
   -------------------------- */

private fun Float.coerceFinite(): Float =
    if (this.isFinite()) this else 1f

@Composable
private fun DeleteStickerButtonOverlay(
    spec: CardSpecPx,
    boxSize: IntSize,
    cardData: CardDataPx,
    activeStickerId: Long?,
    onDelete: (Long) -> Unit
) {
    if (activeStickerId == null) return

    val s = cardData.stickers.firstOrNull { it.id == activeStickerId } ?: return

    // compute px position
    val pxX = s.xNorm * boxSize.width
    val pxY = s.yNorm * boxSize.height
    val offset = 40.dp      // always fixed size

    Box(
        modifier = Modifier
            .offset(
                x = (pxX.dp + offset),
                y = (pxY.dp - offset)
            )
    ) {
        IconButton(onClick = { onDelete(s.id) }) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete sticker",
                tint = Color.Red,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
