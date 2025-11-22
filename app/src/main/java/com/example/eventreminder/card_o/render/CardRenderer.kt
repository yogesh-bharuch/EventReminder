package com.example.eventreminder.card_o.render

// =============================================================
// Imports
// =============================================================
import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.toArgb
import com.example.eventreminder.card_o.model.CardRenderRequest
import timber.log.Timber
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardRenderer"

/**
 * CardRenderer
 *
 * Responsible for rendering:
 *  - Background color + gradient
 *  - Main card panel
 *  - Photo layer
 *  - Stickers
 *  - Text blocks using TextLayoutEngine
 *  - Optional relation icon
 */
object CardRenderer {

    // =============================================================
    // Core Render API
    // =============================================================
    fun renderToBitmap(context: Context, req: CardRenderRequest): Bitmap {
        Timber.tag(TAG).d("renderToBitmap - start for ${req.recipientName}")

        val width = req.width
        val height = req.height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // =============================================================
        // 1. Background Color
        // =============================================================
        run {
            val bgPaint = Paint().apply {
                style = Paint.Style.FILL
                color = req.theme.backgroundColor.toArgb()
                isAntiAlias = true
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        }

        // =============================================================
        // 2. Background Gradient Overlay
        // =============================================================
        run {
            try {
                val accent = req.theme.accentColor.toArgb()
                val bg = req.theme.backgroundColor.toArgb()

                val shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(accent, bg),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )

                val paint = Paint().apply {
                    isAntiAlias = true
                    this.shader = shader
                }

                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Gradient failed — using flat background only")
            }
        }

        // =============================================================
        // 3. Inner Card Panel
        // =============================================================
        val inset = width * 0.07f
        val cardRect = RectF(inset, inset, width - inset, height - inset)

        // Shadow
        run {
            val shadowPaint = Paint().apply {
                isAntiAlias = true
                color = Color.argb(30, 0, 0, 0)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                cardRect.left + 8f,
                cardRect.top + 8f,
                cardRect.right + 8f,
                cardRect.bottom + 8f,
                36f, 36f, shadowPaint
            )
        }

        // White Panel
        run {
            val panelPaint = Paint().apply {
                isAntiAlias = true
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(cardRect, 30f, 30f, panelPaint)
        }

        // =============================================================
        // 4. Photo Layer
        // =============================================================
        req.photoLayer?.let { layer ->
            val uri = layer.photoUri

            if (uri != null) {
                val targetSize = layer.size.toInt()
                val loaded = PhotoLoader.loadBitmap(context, uri, targetSize)

                if (loaded != null) {

                    val cropped = PhotoCropper.crop(loaded, layer)

                    canvas.drawBitmap(cropped, layer.x, layer.y, null)

                    if (!cropped.isRecycled) cropped.recycle()
                    if (!loaded.isRecycled) loaded.recycle()

                    Timber.tag(TAG).d("Photo layer drawn at ${layer.x}, ${layer.y}")
                } else {
                    Timber.tag(TAG).w("Photo load FAILED: $uri")
                }
            }
        }

        // =============================================================
        // 5. Stickers
        // =============================================================
        if (req.stickers.isNotEmpty()) {
            Timber.tag(TAG).d("Rendering ${req.stickers.size} stickers")

            req.stickers.forEach { sticker ->
                try {
                    val drawable = context.resources.getDrawable(sticker.drawableResId, context.theme)

                    val iw = drawable.intrinsicWidth
                    val ih = drawable.intrinsicHeight
                    if (iw <= 0 || ih <= 0) {
                        Timber.tag(TAG).w("Invalid sticker intrinsic size: ${sticker.drawableResId}")
                        return@forEach
                    }

                    val scaledW = iw * sticker.scale
                    val scaledH = ih * sticker.scale

                    val rect = RectF(
                        sticker.x,
                        sticker.y,
                        sticker.x + scaledW,
                        sticker.y + scaledH
                    )

                    canvas.save()
                    canvas.rotate(sticker.rotation, rect.centerX(), rect.centerY())

                    drawable.setBounds(
                        rect.left.toInt(),
                        rect.top.toInt(),
                        rect.right.toInt(),
                        rect.bottom.toInt()
                    )
                    drawable.draw(canvas)

                    canvas.restore()

                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Sticker render failed: ${sticker.drawableResId}")
                }
            }
        }

        // =============================================================
        // 6. Text Blocks (StaticLayout Engine)
        // =============================================================
        val baseTextPaint = Paint().apply {
            isAntiAlias = true
            color = req.theme.accentColor.toArgb()
            textAlign = Paint.Align.LEFT
        }

        req.textBlocks.forEach { tb ->
            val tp = Paint(baseTextPaint).apply {
                textSize = tb.fontSize.value
                typeface = when (tb.fontWeight) {
                    androidx.compose.ui.text.font.FontWeight.Bold ->
                        Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                    androidx.compose.ui.text.font.FontWeight.ExtraBold ->
                        Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

                    else ->
                        Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                }
                color = tb.color.toArgb()
            }

            TextLayoutEngine.drawTextBlock(
                canvas = canvas,
                text = tb.text,
                x = tb.x,
                y = tb.y,
                paint = tp,
                maxWidth = tb.maxWidth,
                alignment = tb.alignment,
                autoFit = tb.autoFit
            )
        }

        // =============================================================
        // 7. Relation Icon
        // =============================================================
        req.relationIcon?.let { rIcon ->
            try {
                val drawable = context.resources.getDrawable(rIcon.drawableResId, context.theme)

                val iconSize = (width * 0.12f).toInt()
                val left = (cardRect.right - iconSize - 36f).toInt()
                val top = (cardRect.top + 36f).toInt()

                drawable.setBounds(left, top, left + iconSize, top + iconSize)
                drawable.draw(canvas)

            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Relation icon failed to draw")
            }
        }

        Timber.tag(TAG).d("renderToBitmap - finished")
        return bitmap
    }

    // =============================================================
    // Save to Gallery
    // =============================================================
    suspend fun renderAndSaveToGallery(
        context: Context,
        req: CardRenderRequest
    ): android.net.Uri? {

        val bitmap = try {
            renderToBitmap(context, req)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Rendering failed")
            return null
        }

        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val name = "card_${sdf.format(Date())}.png"

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/BirthdayCards")
            }
        }

        val collection =
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        var outUri: android.net.Uri? = null

        try {
            outUri = resolver.insert(collection, values)
            if (outUri == null) {
                Timber.tag(TAG).e("MediaStore insert failed for $name")
                bitmap.recycle()
                return null
            }

            resolver.openOutputStream(outUri).use { out ->
                if (out == null) {
                    Timber.tag(TAG).e("Null outputStream for $outUri")
                    bitmap.recycle()
                    return null
                }

                val ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()

                if (!ok) Timber.tag(TAG).w("Bitmap.compress returned false")
            }

        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "Error saving bitmap")
            outUri = null

        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }

        return outUri
    }
}
