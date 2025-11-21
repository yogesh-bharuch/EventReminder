package com.example.eventreminder.cards.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*

// =============================================================
// Constants
// =============================================================
private const val TAG = "ImageUtil"

// =============================================================
// Image / Bitmap helpers used by Card image pipeline
// =============================================================
object ImageUtil {

    /**
     * Load a reasonably sized bitmap from [uri]. This avoids OOM by scaling down
     * if the image is very large. Returns null on failure.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? {
        return try {
            val resolver: ContentResolver = context.contentResolver
            // Decode bounds first
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // Compute sample size
            var sample = 1
            val w = options.outWidth
            val h = options.outHeight
            val largest = maxOf(w, h)
            if (largest > maxDim) {
                sample = 1
                while (largest / sample > maxDim) {
                    sample *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            resolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to load bitmap from uri: $uri")
            null
        }
    }

    /**
     * Center-crop a bitmap to a square of size [size]. If size < 1, uses min(width,height).
     */
    fun centerCropSquare(src: Bitmap, size: Int = 0): Bitmap {
        val srcW = src.width
        val srcH = src.height
        val min = minOf(srcW, srcH)
        val outSize = if (size > 0) size else min
        val left = (srcW - min) / 2
        val top = (srcH - min) / 2

        val cropped = Bitmap.createBitmap(src, left, top, min, min)
        return if (outSize == min) cropped else Bitmap.createScaledBitmap(cropped, outSize, outSize, true)
    }

    /**
     * Convert a square bitmap into a circular bitmap with anti-aliasing.
     */
    fun toCircularBitmap(srcSquare: Bitmap): Bitmap {
        val size = minOf(srcSquare.width, srcSquare.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        val radius = size / 2f

        // Draw circle mask
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = Color.WHITE
        canvas.drawCircle(radius, radius, radius, paint)

        // Use SRC_IN to keep only intersection
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(srcSquare, null, rect, paint)
        paint.xfermode = null

        return output
    }

    /**
     * Save bitmap to app cache directory and return a content Uri (file://).
     * Note: returns the absolute file path string for compatibility with existing code.
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, filenamePrefix: String = "avatar_"): String? {
        return try {
            val cacheDir = File(context.cacheDir, "avatars").apply { if (!exists()) mkdirs() }
            val name = "$filenamePrefix${UUID.randomUUID()}.png"
            val file = File(cacheDir, name)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                out.flush()
            }
            Timber.tag(TAG).d("Saved avatar to cache → ${file.absolutePath}")
            file.absolutePath
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to save avatar to cache")
            null
        }
    }

    /**
     * Load small bitmap from InputStream for preview/transform.
     */
    fun loadBitmapFromStream(stream: InputStream?, maxDim: Int = 1200): Bitmap? {
        if (stream == null) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            stream.mark(1)
            BitmapFactory.decodeStream(stream, null, options)
            stream.reset()
            var sample = 1
            val largest = maxOf(options.outWidth, options.outHeight)
            if (largest > maxDim) {
                while (largest / sample > maxDim) sample *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed load from stream")
            null
        }
    }
}
