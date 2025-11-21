package com.example.eventreminder.cards.capture

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import timber.log.Timber
import java.io.OutputStream

private const val TAG = "BitmapSaver"

/**
 * Saves bitmap to Pictures/EventReminderCards
 */
object BitmapSaver {

    fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        filename: String
    ): String? {
        return try {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "Pictures/EventReminderCards"
                    )
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null

            // FIX: Ensure OutputStream is non-null before using it
            resolver.openOutputStream(uri)?.use { out: OutputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return null

            Toast.makeText(context, "Saved to gallery", Toast.LENGTH_SHORT).show()
            uri.toString()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "saveToGallery failed")
            null
        }
    }
}
