package com.example.eventreminder.card_o.render

// region Imports
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import timber.log.Timber
import java.io.IOException
// endregion

// region Constants
private const val TAG = "PhotoLoader"
// endregion

/**
 * PhotoLoader
 *
 * Loads a Bitmap from a Uri, downscaled to a target size.
 * Helps avoid OOM issues.
 */
object PhotoLoader {

    /**
     * Load and downscale an image from Uri.
     *
     * @param context Android context
     * @param uri Uri of the image
     * @param targetSize size in px for width and height (square)
     */
    fun loadBitmap(
        context: Context,
        uri: Uri,
        targetSize: Int
    ): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) {
                    Timber.tag(TAG).w("Null stream for uri=$uri")
                    return null
                }

                // Decode original
                val original = BitmapFactory.decodeStream(input) ?: return null

                // Scale down
                val scaled = Bitmap.createScaledBitmap(
                    original,
                    targetSize,
                    targetSize,
                    true
                )

                if (!original.isRecycled) original.recycle()
                Timber.tag(TAG).d("Loaded & scaled photo: ${scaled.width}x${scaled.height}")
                scaled
            }
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "Error loading uri=$uri")
            null
        }
    }
}
