package com.example.eventreminder.cards.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*

private const val TAG = "ImageUtil"

/**
 * Lightweight image helpers used by card pipeline.
 * Designed for reliability and to avoid OOM by downsampling.
 */
object ImageUtil {

    /**
     * Load a reasonably sized bitmap from [uri]. Returns null on failure.
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
            if (largest > maxDim && largest > 0) {
                sample = 1
                while (largest / sample > maxDim) sample *= 2
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
     * Center-crop a bitmap to a square.
     */
    fun centerCropSquare(src: Bitmap, size: Int = 0): Bitmap {
        val srcW = src.width
        val srcH = src.height
        val min = kotlin.math.min(srcW, srcH)
        val outSize = if (size > 0) size else min
        val left = (srcW - min) / 2
        val top = (srcH - min) / 2
        val cropped = Bitmap.createBitmap(src, left, top, min, min)
        return if (outSize == min) cropped else Bitmap.createScaledBitmap(cropped, outSize, outSize, true)
    }

    /**
     * Convert square bitmap to circular bitmap.
     */
    fun toCircularBitmap(srcSquare: Bitmap): Bitmap {
        val size = kotlin.math.min(srcSquare.width, srcSquare.height)
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
     * Save bitmap to app cache directory and return absolute file path.
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, filenamePrefix: String = "avatar_"): String? {
        return try {
            val cacheDir = File(context.cacheDir, "card_cache").apply { if (!exists()) mkdirs() }
            val name = "$filenamePrefix${UUID.randomUUID()}.png"
            val file = File(cacheDir, name)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                out.flush()
            }
            Timber.tag(TAG).d("Saved bitmap to cache → ${file.absolutePath}")
            file.absolutePath
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to save bitmap to cache")
            null
        }
    }

    /**
     * Helper to load a bitmap from a string path which may be content://, file:// or absolute path.
     */
    fun loadBitmapFromPathString(context: Context, pathStr: String?, maxDim: Int = 1600): Bitmap? {
        if (pathStr.isNullOrBlank()) return null
        return try {
            val uri = when {
                pathStr.startsWith("content://") || pathStr.startsWith("file://") || pathStr.startsWith("http") ->
                    Uri.parse(pathStr)
                else -> Uri.fromFile(File(pathStr))
            }
            loadBitmapFromUri(context, uri, maxDim)
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "loadBitmapFromPathString failed for: $pathStr")
            null
        }
    }
}







/*
package com.example.eventreminder.cards.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.*
import android.net.Uri
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.*

private const val TAG = "ImageUtil"

*/
/**
 * Image / Bitmap helpers used by Card image pipeline
 *//*

object ImageUtil {

    // ========================================================================
    // PUBLIC API — Load bitmap from URI (content:// or file://)
    // ========================================================================
    fun loadBitmapFromUri(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap? {
        return try {
            val resolver: ContentResolver = context.contentResolver

            // Decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) return null

            // Compute sample size
            var sample = 1
            val largest = maxOf(w, h)
            while (largest / sample > maxDim) {
                sample *= 2
            }

            // Decode actual bitmap
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            resolver.openInputStream(uri).use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }

        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to load bitmap from uri: $uri")
            null
        }
    }

    // ========================================================================
    // NEW — Load bitmap from a file path string (used for saved backgrounds)
    // ========================================================================
    fun loadBitmapFromPath(path: String, maxDim: Int = 2000): Bitmap? {
        return try {
            val file = File(path)
            if (!file.exists()) {
                Timber.tag(TAG).e("File does not exist: $path")
                return null
            }

            // First decode bounds
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            FileInputStream(file).use { input ->
                BitmapFactory.decodeStream(input, null, boundsOpts)
            }

            val w = boundsOpts.outWidth
            val h = boundsOpts.outHeight
            if (w <= 0 || h <= 0) return null

            // Compute sample size
            var sample = 1
            val largest = maxOf(w, h)
            while (largest / sample > maxDim) {
                sample *= 2
            }

            // Decode actual bitmap
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            FileInputStream(file).use { input ->
                BitmapFactory.decodeStream(input, null, decodeOpts)
            }

        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to load bitmap from file: $path")
            null
        }
    }

    // ========================================================================
    // NEW — Generic internal loader (ViewModel safe)
    // Accepts a URI string (content:// OR file path)
    // ========================================================================
    fun loadBitmapFromUriInternal(context: Context, uriString: String, maxDim: Int = 2000): Bitmap? {
        return try {
            val uri = when {
                uriString.startsWith("content://") || uriString.startsWith("file://") ->
                    Uri.parse(uriString)
                else ->
                    Uri.fromFile(File(uriString)) // plain path
            }

            // Use the same robust method as loadBitmapFromUri
            if (uri.scheme?.startsWith("content") == true) {
                loadBitmapFromUri(context, uri, maxDim)
            } else {
                loadBitmapFromPath(uri.path ?: uriString, maxDim)
            }

        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "loadBitmapFromUriInternal failed for $uriString")
            null
        }
    }

    // ========================================================================
    // Crop to a center square
    // ========================================================================
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

    // ========================================================================
    // Convert square bitmap → circular
    // ========================================================================
    fun toCircularBitmap(srcSquare: Bitmap): Bitmap {
        val size = minOf(srcSquare.width, srcSquare.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        val radius = size / 2f

        canvas.drawARGB(0, 0, 0, 0)
        paint.color = Color.WHITE
        canvas.drawCircle(radius, radius, radius, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(srcSquare, null, rect, paint)
        paint.xfermode = null

        return output
    }

    // ========================================================================
    // Save bitmap to cache
    // ========================================================================
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, filenamePrefix: String = "img_"): String? {
        return try {
            val cacheDir = File(context.cacheDir, "card_images").apply { if (!exists()) mkdirs() }
            val name = "$filenamePrefix${UUID.randomUUID()}.jpg"
            val file = File(cacheDir, name)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
            }

            Timber.tag(TAG).d("Saved bitmap to cache → ${file.absolutePath}")
            file.absolutePath

        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to save bitmap to cache")
            null
        }
    }

    // ========================================================================
    // Load from InputStream
    // ========================================================================
    fun loadBitmapFromStream(stream: InputStream?, maxDim: Int = 1200): Bitmap? {
        if (stream == null) return null
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            stream.mark(1)
            BitmapFactory.decodeStream(stream, null, options)
            stream.reset()

            var sample = 1
            val largest = maxOf(options.outWidth, options.outHeight)
            while (largest / sample > maxDim) sample *= 2

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            BitmapFactory.decodeStream(stream, null, decodeOpts)

        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed load from stream")
            null
        }
    }
}
*/
