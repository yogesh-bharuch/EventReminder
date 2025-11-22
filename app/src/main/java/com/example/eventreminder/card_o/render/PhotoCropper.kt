package com.example.eventreminder.card_o.render

// region Imports
import android.graphics.*
import timber.log.Timber
import com.example.eventreminder.card_o.model.CardPhotoLayer
// endregion

// region Constants
private const val TAG = "PhotoCropper"
// endregion

/**
 * PhotoCropper
 *
 * Handles circular and square cropping for user photos.
 */
object PhotoCropper {

    fun crop(bitmap: Bitmap, layer: CardPhotoLayer): Bitmap {

        val size = layer.size.toInt()
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Prepare a shader to draw the bitmap scaled into the output
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        val matrix = Matrix().apply {
            val scale = size.toFloat() / bitmap.width.coerceAtLeast(bitmap.height)
            setScale(scale, scale)
        }

        shader.setLocalMatrix(matrix)
        val paint = Paint().apply {
            isAntiAlias = true
            this.shader = shader
        }

        when (layer.cropType) {

            CardPhotoLayer.CropType.Circle -> {
                val radius = size / 2f
                canvas.drawCircle(radius, radius, radius, paint)

                // Border
                if (layer.borderColor != null && layer.borderWidth > 0f) {
                    val borderPaint = Paint().apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        color = layer.borderColor
                        strokeWidth = layer.borderWidth
                    }
                    canvas.drawCircle(radius, radius, radius - layer.borderWidth / 2f, borderPaint)
                }
            }

            CardPhotoLayer.CropType.Square -> {
                val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
                canvas.drawRect(rect, paint)

                // Border
                if (layer.borderColor != null && layer.borderWidth > 0f) {
                    val borderPaint = Paint().apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        color = layer.borderColor
                        strokeWidth = layer.borderWidth
                    }
                    canvas.drawRect(rect, borderPaint)
                }
            }
        }

        Timber.tag(TAG).d("Photo cropped: ${layer.cropType}")
        return output
    }
}
