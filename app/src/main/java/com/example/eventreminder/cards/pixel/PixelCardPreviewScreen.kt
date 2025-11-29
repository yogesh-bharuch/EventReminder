package com.example.eventreminder.cards.pixel

// =============================================================
// PixelCardPreviewScreen.kt (SAF Save -> remember Documents tree)
// - First Save: ask user to pick a folder (OpenDocumentTree)
// - Persist tree URI into DataStore (SafStorageHelper)
// - Create Documents/EventReminderCards inside chosen tree (if missing)
// - Save PNG via SAF OutputStream (guaranteed visible on device)
// - Share through private temp copy (FileProvider) to avoid FileProvider issues with public files
// =============================================================

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.eventreminder.cards.util.ImageUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

private const val TAG = "PixelCardPreviewScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelCardPreviewScreen() {
    Timber.tag(TAG).d("PixelCardPreviewScreen (SAF) loaded")

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ----------------------------------------------------------------
    // Canonical spec (1080x1200)
    // ----------------------------------------------------------------
    val spec = remember { CardSpecPx.default1080x1200() }

    // ----------------------------------------------------------------
    // CardData state
    // ----------------------------------------------------------------
    var cardData by remember {
        mutableStateOf(
            CardDataPx(
                reminderId = 999L,
                titleText = "Happy Birthday",
                nameText = "Yogesh",
                showTitle = true,
                showName = true,
                avatarBitmap = null,
                backgroundBitmap = null,
                stickers = emptyList(),
                originalDateLabel = "Jan 1, 1990",
                nextDateLabel = "Fri, Jan 1, 2026",
                ageOrYearsLabel = "34"
            )
        )
    }

    // ----------------------------------------------------------------
    // Background picker (image)
    // ----------------------------------------------------------------
    val backgroundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            Timber.tag(TAG).d("BG picked: $uri")
            val bmp = ImageUtil.loadBitmapFromUri(context, uri, 2000)
            if (bmp != null) {
                cardData = cardData.copy(backgroundBitmap = bmp)
            }
        }
    }

    // ----------------------------------------------------------------
    // OpenDocumentTree launcher (SAF) — used only on first save to pick Documents folder
    // ----------------------------------------------------------------
    val openTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri == null) {
            Timber.tag(TAG).d("User cancelled tree selection")
            return@rememberLauncherForActivityResult
        }

        // Persist permissions and store URI to DataStore for future saves
        try {
            // ask for persistable permissions
            val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            context.contentResolver.takePersistableUriPermission(treeUri, flags)

            // save treeUri into DataStore
            coroutineScope.launch(Dispatchers.IO) {
                SafStorageHelper.saveTreeUri(context, treeUri.toString())
                Timber.tag(TAG).d("Saved SAF tree URI to DataStore: $treeUri")
                // After saving, perform the pending save operation that triggered the picker
            }

            Toast.makeText(context, "Folder selected. Ready to save.", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to persist tree URI")
            Toast.makeText(context, "Failed to take permission for folder", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        // Load placeholder avatar only once on entry
        viewModel.loadPixelAvatarPlaceholder()
    }

    // ----------------------------------------------------------------
    // Helper: create (or find) EventReminderCards folder inside the chosen tree
    // Returns a DocumentFile reference for the folder or null on failure
    // ----------------------------------------------------------------
    suspend fun ensureEventFolder(treeUri: Uri): DocumentFile? {
        return try {
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (treeDoc == null) {
                Timber.tag(TAG).w("DocumentFile.fromTreeUri returned null")
                return null
            }

            // Try to find existing folder
            val existing = treeDoc.findFile("EventReminderCards")
            if (existing != null && existing.isDirectory) {
                return existing
            }

            // Create folder
            val created = treeDoc.createDirectory("EventReminderCards")
            if (created == null) {
                Timber.tag(TAG).w("Failed to create EventReminderCards folder")
            } else {
                Timber.tag(TAG).d("Created EventReminderCards folder")
            }
            created
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "ensureEventFolder failed")
            null
        }
    }

    // ----------------------------------------------------------------
    // Save PNG into the persisted SAF tree under EventReminderCards
    // - If no tree saved, returns null (caller should launch openTreeLauncher)
    // - Uses SAF OutputStream, guaranteed visible on device
    // ----------------------------------------------------------------
    suspend fun savePngViaSaf(): Uri? {
        try {
            // read saved treeUri from DataStore (suspending)
            val treeUri = SafStorageHelper.getTreeUriFlow(context).first()
            if (treeUri == null) {
                Timber.tag(TAG).d("No saved tree URI")
                return null
            }

            // ensure EventReminderCards inside the tree
            val folder = ensureEventFolder(treeUri) ?: return null

            // produce filename and create new file inside folder
            val filename = "Card_${System.currentTimeMillis()}.png"
            val mime = "image/png"

            // createFile returns DocumentFile representing the newly created file
            val newFile = folder.createFile(mime, filename)
            if (newFile == null) {
                Timber.tag(TAG).e("createFile returned null")
                return null
            }

            // write PNG bytes into newFile via contentResolver
            val out: OutputStream? = context.contentResolver.openOutputStream(newFile.uri)
            if (out == null) {
                Timber.tag(TAG).e("openOutputStream returned null")
                return null
            }

            // Render bitmap into an in-memory bitmap and write
            val bmp = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            PixelRenderer.renderToAndroidCanvas(canvas, spec, cardData)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()

            Timber.tag(TAG).d("Saved via SAF to: ${newFile.uri}")
            return newFile.uri

        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "savePngViaSaf failed")
            return null
        }
    }

    // ----------------------------------------------------------------
    // Public wrapper for Save button: tries SAF fast path (uses saved tree),
    // otherwise launches the folder picker and informs the user.
    // ----------------------------------------------------------------
    fun onSaveClicked() {
        coroutineScope.launch {
            // If we have a persisted tree URI, attempt to save directly
            val treeUri = SafStorageHelper.getTreeUriFlow(context).first()
            if (treeUri != null) {
                Timber.tag(TAG).d("Found saved tree URI — saving directly")
                val uri = savePngViaSaf()
                if (uri != null) {
                    Toast.makeText(context, "Saved to Documents/EventReminderCards", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Save failed; try selecting folder again.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            // No saved tree — ask user to select Documents (or any folder)
            Toast.makeText(context, "Please choose the Documents folder (select base folder).", Toast.LENGTH_LONG).show()

            // Launch folder picker (the result handler will persist the permission and tree URI)
            openTreeLauncher.launch(null)
        }
    }

    // ----------------------------------------------------------------
    // SHARE: create a private app copy and share via FileProvider (stable)
    // ----------------------------------------------------------------
    fun onShareClicked() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Render bitmap in memory
                val bmp = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                PixelRenderer.renderToAndroidCanvas(canvas, spec, cardData)

                // temp file in cacheDir
                val cacheFile = File(context.cacheDir, "share_${System.currentTimeMillis()}.png")
                FileOutputStream(cacheFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                // FileProvider to get secure URI
                val uri = FileProvider.getUriForFile(
                    context,
                    "com.example.eventreminder.fileprovider",
                    cacheFile
                )

                // Share on main thread
                coroutineScope.launch(Dispatchers.Main) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Card PNG"))
                }

            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "onShareClicked failed")
                coroutineScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Share failed: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ----------------------------------------------------------------
    // UI: Compose layout — PixelCanvas + controls
    // ----------------------------------------------------------------
    Scaffold(
        topBar = { TopAppBar(title = { Text("Pixel Renderer – SAF Save") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF5F5F5)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Below is the PixelRenderer output (scaled).",
                color = Color.DarkGray
            )

            Spacer(Modifier.height(16.dp))

            // PixelCanvas preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .aspectRatio(1080f / 1200f)
                    .background(Color.LightGray)
            ) {
                PixelCanvas(spec = spec, data = cardData, modifier = Modifier.fillMaxSize())
            }

            Spacer(Modifier.height(20.dp))

            // BG controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    backgroundPicker.launch(
                        ActivityResultContracts.PickVisualMedia.ImageOnly.let { t ->
                            androidx.activity.result.PickVisualMediaRequest(t)
                        }
                    )
                }) { Text("Pick Bg") }

                Button(onClick = { cardData = cardData.copy(backgroundBitmap = null) }) {
                    Text("Clear Bg")
                }
            }

            Spacer(Modifier.height(20.dp))

            // SAVE + SHARE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { onSaveClicked() }) {
                    Text("Save PNG")
                }

                Button(onClick = { onShareClicked() }) {
                    Text("Share PNG")
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
