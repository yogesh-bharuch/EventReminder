package com.example.eventreminder.card_o.model

// region Imports
import android.net.Uri
import timber.log.Timber
// endregion

// region Constants
private const val TAG = "CardPhotoLayer"
// endregion

/**
 * CardPhotoLayer
 *
 * Represents the user's photo added to the card.
 * Supports square or circle cropping, scaling, and positioning.
 */
data class CardPhotoLayer(

    // URI of the selected/cropped user photo
    val photoUri: Uri? = null,

    // Circle or square crop
    val cropType: CropType = CropType.Circle,

    // X/Y coordinates for placement on canvas
    val x: Float = 0f,
    val y: Float = 0f,

    // Desired final size in pixels (width = height)
    val size: Float = 300f,

    // Optional border around the image
    val borderColor: Int? = null,
    val borderWidth: Float = 0f
) {

    init {
        Timber.tag(TAG).d("PhotoLayer created: uri=$photoUri crop=$cropType size=$size")
    }

    /**
     * Defines how the image should be cropped.
     */
    enum class CropType {
        Circle,
        Square
    }
}
