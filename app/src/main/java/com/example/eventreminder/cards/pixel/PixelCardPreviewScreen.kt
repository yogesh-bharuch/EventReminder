package com.example.eventreminder.cards.pixel

// =============================================================
// PixelCardPreviewScreen.kt
// - Preview UI for PixelRenderer output (scaled in Compose).
// - Loads placeholder avatar into the ViewModel pipeline.
// - Gesture handling (pan/zoom/rotate) forwards normalized deltas to VM.
// - SAF save/share preserved.
// - Gesture modifier is placed last in the Box modifier chain so it sits
//   above the rendered canvas and receives pointer events.
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
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventreminder.cards.CardViewModel
import com.example.eventreminder.cards.util.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.min

private const val TAG = "PixelCardPreviewScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelCardPreviewScreen() {
    Timber.tag(TAG).d("PixelCardPreviewScreen Loaded")

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: CardViewModel = hiltViewModel()
    //var isAvatarGestureActive by remember { mutableStateOf(false) }

    // Ensure placeholder avatar loaded into VM once
    val vmAvatarBitmap by viewModel.pixelAvatarBitmap.collectAsState()

    // Load placeholder ONLY if no avatar has been set yet
    /*LaunchedEffect(vmAvatarBitmap) {
        if (vmAvatarBitmap == null) {
            delay(50)
            Timber.tag("TESTPH").d("No avatar present → loading placeholder")
            viewModel.loadPixelAvatarPlaceholder()
        } else {
            Timber.tag("TESTPH").d("Avatar exists → skipping placeholder load")
        }
    }*/



    // Debug: confirm bitmap observed
    /*LaunchedEffect(vmAvatarBitmap) {
        Timber.tag("TESTPH").d("vmAvatarBitmap present? = ${vmAvatarBitmap != null}")
    }*/

    // Canonical spec (1080x1200)
    val spec = remember { CardSpecPx.default1080x1200() }

    // Observe VM pixel avatar bitmap
    //val vmAvatarBitmap by viewModel.pixelAvatarBitmap.collectAsState()
    LaunchedEffect(vmAvatarBitmap) {
        Timber.tag(TAG).d("VM avatar bitmap present? = ${vmAvatarBitmap != null}")
    }

    // Observe 4 transform values directly from VM
    val xNorm by viewModel.pixelAvatarXNorm.collectAsState()
    val yNorm by viewModel.pixelAvatarYNorm.collectAsState()
    val scale by viewModel.pixelAvatarScale.collectAsState()
    val rotation by viewModel.pixelAvatarRotationDeg.collectAsState()

    // Build transform for CardDataPx
    val vmTransform = AvatarTransformPx(
        xNorm = xNorm,
        yNorm = yNorm,
        scale = scale,
        rotationDeg = rotation
    )


    // Local CardDataPx used by PixelCanvas; keep in sync with VM
    var cardData by remember {
        mutableStateOf(
            CardDataPx(
                reminderId = 999L,
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

    // Mirror VM avatar bitmap + transform into cardData (triggers recomposition & PixelCanvas redraw)
    LaunchedEffect(vmAvatarBitmap, vmTransform) {
        cardData = cardData.copy(
            avatarBitmap = vmAvatarBitmap,
            avatarTransform = vmTransform
        )
        Timber.tag(TAG).d("cardData updated — avatarBitmap null? = ${vmAvatarBitmap == null}")
    }

    // Background image picker
    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        Timber.tag(TAG).d("BG picked: $uri")
        val bmp = try {
            ImageUtil.loadBitmapFromUri(context, uri, maxDim = 2000)
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "BG decode failed")
            null
        }
        if (bmp != null) cardData = cardData.copy(backgroundBitmap = bmp)
        else Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
    }

    // SAF folder launcher to store tree URI (persistable)
    val openTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri == null) {
            Timber.tag(TAG).d("User cancelled tree selection")
            return@rememberLauncherForActivityResult
        }
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            scope.launch(Dispatchers.IO) {
                SafStorageHelper.saveTreeUri(context, treeUri.toString())
                Timber.tag(TAG).d("Saved SAF tree URI to DataStore: $treeUri")
            }
            Toast.makeText(context, "Folder selected. Ready to save.", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to persist tree URI")
            Toast.makeText(context, "Failed to take permission for folder", Toast.LENGTH_LONG).show()
        }
    }

    // Helper to ensure a subfolder exists inside the saved tree
    suspend fun ensureEventFolder(treeUri: Uri): DocumentFile? {
        return try {
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri) ?: run {
                Timber.tag(TAG).w("DocumentFile.fromTreeUri returned null")
                return null
            }
            val existing = treeDoc.findFile("EventReminderCards")
            if (existing != null && existing.isDirectory) return existing
            treeDoc.createDirectory("EventReminderCards").also {
                Timber.tag(TAG).d("Created EventReminderCards folder: $it")
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "ensureEventFolder failed")
            null
        }
    }

    // Save function using saved tree URI
    suspend fun savePngViaSaf(): Uri? {
        return try {
            val savedUri: Uri? = SafStorageHelper.getTreeUriFlow(context).first()
            if (savedUri == null) {
                Timber.tag(TAG).d("No saved tree URI")
                return null
            }
            val folder = ensureEventFolder(savedUri) ?: return null
            val filename = "Card_${System.currentTimeMillis()}.png"
            val newFile = folder.createFile("image/png", filename) ?: run {
                Timber.tag(TAG).e("createFile returned null")
                return null
            }
            val out: OutputStream? = context.contentResolver.openOutputStream(newFile.uri)
            if (out == null) {
                Timber.tag(TAG).e("openOutputStream returned null")
                return null
            }
            val bmp = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            PixelRenderer.renderToAndroidCanvas(canvas, spec, cardData)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()
            Timber.tag(TAG).d("Saved via SAF to: ${newFile.uri}")
            newFile.uri
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "savePngViaSaf failed")
            null
        }
    }

    // Save button wrapper
    fun onSaveClicked() {
        scope.launch {
            val savedUri: Uri? = SafStorageHelper.getTreeUriFlow(context).first()
            if (savedUri != null) {
                val uri = savePngViaSaf()
                if (uri != null) Toast.makeText(context, "Saved to Documents/EventReminderCards", Toast.LENGTH_LONG).show()
                else Toast.makeText(context, "Save failed; try selecting folder again.", Toast.LENGTH_LONG).show()
                return@launch
            }
            Toast.makeText(context, "Please choose the Documents folder (select base folder).", Toast.LENGTH_LONG).show()
            openTreeLauncher.launch(null)
        }
    }

    // Share wrapper (cache + FileProvider)
    fun onShareClicked() {
        scope.launch(Dispatchers.IO) {
            try {
                val bmp = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                PixelRenderer.renderToAndroidCanvas(canvas, spec, cardData)
                val cacheFile = File(context.cacheDir, "share_${System.currentTimeMillis()}.png")
                FileOutputStream(cacheFile).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
                scope.launch {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Card PNG"))
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "onShareClicked failed")
                scope.launch { Toast.makeText(context, "Share failed: ${t.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    // -------------------------
    // Gesture layer (FINAL, FIXED)
    // -------------------------
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    var isAvatarGestureActive by remember { mutableStateOf(false) }

    val gestureModifier = Modifier.pointerInput(spec, boxSize) {
        awaitPointerEventScope {
            while (true) {

                // Wait for first press
                var event = awaitPointerEvent()
                val firstDown = event.changes.firstOrNull { it.pressed } ?: continue

                val touchX = firstDown.position.x
                val touchY = firstDown.position.y

                // --------------------------------------------
                // ⭐ Accurate matrix-based hit-testing
                // --------------------------------------------
                isAvatarGestureActive = PixelRenderer.isTouchInsideAvatar(
                    touchX = touchX,
                    touchY = touchY,
                    spec = spec,
                    data = cardData
                )

                // If touch is NOT inside avatar → ignore gesture
                if (!isAvatarGestureActive) {
                    // Wait until all fingers released
                    while (event.changes.any { it.pressed }) {
                        event = awaitPointerEvent()
                    }
                    continue
                }

                // --------------------------------------------
                // ⭐ AVATAR GESTURES (pan/zoom/rotate)
                // --------------------------------------------
                while (event.changes.any { it.pressed }) {

                    val pan = event.calculatePan()
                    val zoom = event.calculateZoom()
                    val rotation = event.calculateRotation()

                    val dxNorm = pan.x / boxSize.width.toFloat()
                    val dyNorm = pan.y / boxSize.height.toFloat()

                    viewModel.updatePixelAvatarPosition(dxNorm, dyNorm)
                    viewModel.updatePixelAvatarScale(zoom)
                    viewModel.updatePixelAvatarRotation(rotation)

                    event.changes.forEach { it.consume() }
                    event = awaitPointerEvent()
                }
            }
        }
    }


    // -------------------------
    // UI
    // -------------------------
    Scaffold(
        topBar = { TopAppBar(title = { Text("Pixel Renderer – SAF Save") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF5F5F5)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))

            Text(text = "Below is the PixelRenderer output (scaled).", color = Color.DarkGray)

            Spacer(Modifier.height(16.dp))

            // Container for the PixelCanvas. pointerInput is applied LAST so it receives gestures.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .aspectRatio(1080f / 1200f)
                    .background(Color.LightGray)
                    .onGloballyPositioned { coords ->
                        boxSize = IntSize(coords.size.width, coords.size.height)
                        Timber.tag(TAG).d("Box size = ${boxSize.width} x ${boxSize.height}")
                    }
                    // pointerInput must be last so it intercepts touches on top of PixelCanvas
                    .then(gestureModifier)
            ) {
                // PixelCanvas uses drawIntoCanvas and will redraw when 'cardData' changes
                PixelCanvas(spec = spec, data = cardData, modifier = Modifier.fillMaxSize())
            }

            Spacer(Modifier.height(10.dp))

            // Background controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    backgroundPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Pick Bg") }

                Button(onClick = { cardData = cardData.copy(backgroundBitmap = null) }) {
                    Text("Clear Bg")
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { onSaveClicked() }) { Text("Save PNG") }
                Button(onClick = { onShareClicked() }) { Text("Share PNG") }
            }

            Spacer(Modifier.height(10.dp))

            // -------------------------
            // Avatar controls (Pixel Avatar)
            // -------------------------

            // SAF/PhotoPicker for Pixel Avatar
            val avatarPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia()
            ) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult

                Timber.tag(TAG).d("Avatar picked: $uri")
                viewModel.onPixelAvatarImageSelected(context, uri)
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        avatarPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) { Text("Pick Photo") }

                Button(onClick = { viewModel.clearPixelAvatar() }) {
                    Text("Clear Photo")
                }
            }

        }
    }
}

/* --------------------------
   Small helpers
   -------------------------- */

// Extension to coerce zoom to finite value (avoid NaN/infinity)
private fun Float.coerceFinite(): Float =
    if (this.isFinite()) this else 1f

// Format helper for logs
private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
