package com.example.eventreminder.cards.pixelcanvas.export


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.core.content.FileProvider
import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import com.example.eventreminder.cards.pixelcanvas.canvas.PixelRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

private const val TAG = "CardExportUtils"

object CardExportUtils {

    // ---------------------------------------------------------
    // Ensure root folder /EventReminderCards exists in SAF tree
    // ---------------------------------------------------------
    suspend fun ensureEventFolder(context: Context, treeUri: Uri): DocumentFile? {
        return try {
            val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            val existing = tree.findFile("EventReminderCards")
            if (existing != null && existing.isDirectory) return existing

            tree.createDirectory("EventReminderCards")
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "ensureEventFolder failed")
            null
        }
    }


    // ---------------------------------------------------------
    // SAVE PNG → via SAF Selected Folder
    // ---------------------------------------------------------
    suspend fun savePngToSaf(
        context: Context,
        spec: CardSpecPx,
        cardData: CardDataPx
    ): Uri? = withContext(Dispatchers.IO) {

        try {
            val savedTreeUri = SafStorageHelper.getTreeUriFlow(context).first()
            if (savedTreeUri == null) return@withContext null

            val folder = ensureEventFolder(context, savedTreeUri) ?: return@withContext null

            val filename = "Card_${System.currentTimeMillis()}.png"
            val newFile = folder.createFile("image/png", filename) ?: return@withContext null

            val out: OutputStream = context.contentResolver.openOutputStream(newFile.uri)
                ?: return@withContext null

            val bmp = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            PixelRenderer.renderToAndroidCanvas(canvas, spec, cardData)

            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()

            newFile.uri
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "savePngToSaf failed")
            null
        }
    }


    // ---------------------------------------------------------
    // SHARE PNG → using Cache + FileProvider
    // ---------------------------------------------------------
    suspend fun sharePng(context: Context, spec: CardSpecPx, cardData: CardDataPx): Uri? =
        withContext(Dispatchers.IO) {

            try {
                val bmp = Bitmap.createBitmap(spec.widthPx, spec.heightPx, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                PixelRenderer.renderToAndroidCanvas(canvas, spec, cardData)

                val cacheFile = File(context.cacheDir, "share_${System.currentTimeMillis()}.png")
                FileOutputStream(cacheFile).use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }

                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "sharePng failed")
                null
            }
        }
}
