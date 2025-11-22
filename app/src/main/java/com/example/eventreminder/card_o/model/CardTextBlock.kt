package com.example.eventreminder.card.model

// region Imports
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import timber.log.Timber
// endregion

// region Constants
private const val TAG = "CardTextBlock"
// endregion

/**
 * CardTextBlock
 *
 * Defines a text element that will be rendered on the card.
 * This includes title, subtitle, messages, greetings, age info, etc.
 */
data class CardTextBlock(

    // The actual text content
    val text: String,

    val alignment: TextAlignment = TextAlignment.Left,
    val maxWidth: Float? = null,    // null = no wrapping
    val autoFit: Boolean = false,    // shrink text until fits

    // Text size in SP
    val fontSize: TextUnit = 28.sp,

    // Weight (Bold for header, Normal for body)
    val fontWeight: FontWeight = FontWeight.Normal,

    // Text color
    val color: Color = Color.Black,

    // X/Y position in pixels on the canvas
    val x: Float,
    val y: Float
) {

    init {
        Timber.tag(TAG).d("TextBlock created: \"$text\" at ($x,$y)")
    }
}

enum class TextAlignment {
    Left,
    Center,
    Right
}
