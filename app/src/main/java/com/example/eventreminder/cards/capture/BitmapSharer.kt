package com.example.eventreminder.cards.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

private const val TAG = "BitmapSharer"

object BitmapSharer {

    fun share(context: Context, bitmap: Bitmap, filename: String) {
        try {
            val cacheDir = File(context.cacheDir, "share_cards")
            cacheDir.mkdirs()

            val file = File(cacheDir, "$filename.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(
                Intent.createChooser(intent, "Share card")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Share failed")
        }
    }
}
