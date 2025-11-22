package com.example.eventreminder.cards.util

// =============================================================
// Imports
// =============================================================
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

// =============================================================
// TAG
// =============================================================
private const val TAG = "ImageUtil"

// =============================================================
// ImageUtil — helper decoding / saving utilities
// =============================================================
object ImageUtil {

    /**
     * Load a reasonably sized bitmap from [uri]. Avoid OOM by sampling down.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? {
        return try {
            val resolver: ContentResolver = context.contentResolver
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val w = options.outWidth
            val h = options.outHeight
            val largest = maxOf(w, h).coerceAtLeast(1)
            var sample = 1
            while (largest / sample > maxDim) sample *= 2
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            resolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "loadBitmapFromUri failed for $uri")
            null
        }
    }

    /**
     * Save bitmap to app cache and return absolute file path.
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, filenamePrefix: String = "img_"): String? {
        return try {
            val cacheDir = File(context.cacheDir, "images").apply { if (!exists()) mkdirs() }
            val name = "$filenamePrefix${UUID.randomUUID()}.png"
            val file = File(cacheDir, name)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                out.flush()
            }
            Timber.tag(TAG).d("Saved image to ${file.absolutePath}")
            file.absolutePath
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "saveBitmapToCache failed")
            null
        }
    }

    /**
     * Center-crop a square from [src] optionally scaled to [size]
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
     * Make circular bitmap from square source.
     */
    fun toCircularBitmap(srcSquare: Bitmap): Bitmap {
        val size = minOf(srcSquare.width, srcSquare.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val rect = android.graphics.Rect(0, 0, size, size)
        val radius = size / 2f
        canvas.drawARGB(0, 0, 0, 0)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(srcSquare, null, rect, paint)
        paint.xfermode = null
        return output
    }

    /**
     * Load bitmap from path string that may be either a file path or a content Uri string.
     */
    fun loadBitmapFromPathString(path: String, maxDim: Int = 2000): Bitmap? {
        return try {
            // try file
            val f = File(path)
            if (f.exists()) {
                // decode with sampling
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, opts)
                val largest = maxOf(opts.outWidth, opts.outHeight).coerceAtLeast(1)
                var sample = 1
                while (largest / sample > maxDim) sample *= 2
                val decodeOpt = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                BitmapFactory.decodeFile(f.absolutePath, decodeOpt)
            } else {
                // not a file — try to parse as Uri (content://)
                null
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "loadBitmapFromPathString failed for $path")
            null
        }
    }
}
