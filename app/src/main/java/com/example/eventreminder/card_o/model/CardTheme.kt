package com.example.eventreminder.card_o.model

// region Imports
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import timber.log.Timber
// endregion

// region Constants
private const val TAG = "CardTheme"
// endregion

/**
 * CardTheme
 *
 * Represents a visual theme for the generated card.
 * Defines background, gradient overlays, highlight colors,
 * and optional frame or stylistic accents.
 */
data class CardTheme(

    // Main background color (fallback if no image/gradient)
    val backgroundColor: Color,

    // Optional gradient overlay placed above background
    val gradientOverlay: Brush? = null,

    // Accent color used for titles, headers, frames
    val accentColor: Color = Color.White,

    // Secondary accent (subtitles, decorative strokes)
    val secondaryAccentColor: Color = Color.LightGray,

    // Name of the theme (“Elegant Pink”, “Royal Blue”, etc.)
    val themeName: String
) {

    init {
        Timber.tag(TAG).d("CardTheme created: $themeName")
    }
}
