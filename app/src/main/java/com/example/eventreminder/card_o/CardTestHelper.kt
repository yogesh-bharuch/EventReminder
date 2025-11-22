package com.example.eventreminder.card_o

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.provider.MediaStore
import timber.log.Timber
import java.io.IOException

private const val TAG = "CardTestHelper"

/**
 * Create a blank test card (PNG) and save it to Pictures/BirthdayCards/card_test.png
 * Returns the saved Uri or null on failure.
 */
suspend fun createBlankCardTest(context: Context): android.net.Uri? {
    // Card size (pixels) - typical phone share-friendly size (1080x1080 square)
    val width = 1080
    val height = 1080

    // Create a simple bitmap with a placeholder header text
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Draw background and simple header so file isn't completely empty
    val backgroundPaint = Paint().apply {
        isAntiAlias = true
        // don't set colors/styles that rely on resources - keep simple
        color = 0xFFF5E6FF.toInt() // pale pink-ish (literal hex)
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

    val headerPaint = Paint().apply {
        isAntiAlias = true
        textSize = 48f
        color = 0xFF333333.toInt()
    }
    val subtitlePaint = Paint().apply {
        isAntiAlias = true
        textSize = 28f
        color = 0xFF555555.toInt()
    }

    // Draw rounded sample card area
    val inset = 80f
    val rect = RectF(inset, inset, width - inset, height - inset)
    val cardPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFFFFFFFF.toInt()
    }
    canvas.drawRoundRect(rect, 30f, 30f, cardPaint)

    // Draw text
    canvas.drawText("Birthday Card — Test", inset + 40f, inset + 120f, headerPaint)
    canvas.drawText("Generated: blank run test", inset + 40f, inset + 170f, subtitlePaint)

    // Prepare MediaStore insertion
    val resolver = context.contentResolver
    val displayName = "card_test.png"
    val mimeType = "image/png"

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        // Save under Pictures/BirthdayCards so it shows in Gallery apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/BirthdayCards")
        }
    }

    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    var outUri: android.net.Uri? = null

    try {
        outUri = resolver.insert(collection, values)
        if (outUri == null) {
            Timber.tag(TAG).e("Failed to create MediaStore entry for $displayName")
            bitmap.recycle()
            return null
        }

        resolver.openOutputStream(outUri).use { outStream ->
            if (outStream == null) {
                Timber.tag(TAG).e("Could not open output stream for $outUri")
                bitmap.recycle()
                return null
            }
            // Compress bitmap to PNG
            val success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
            outStream.flush()
            if (!success) {
                Timber.tag(TAG).e("Bitmap.compress() returned false")
            }
        }

        Timber.tag(TAG).d("Saved test card PNG: $outUri")
    } catch (e: IOException) {
        Timber.tag(TAG).e(e, "Error writing PNG")
        outUri = null
    } finally {
        // free bitmap memory
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    return outUri
}
