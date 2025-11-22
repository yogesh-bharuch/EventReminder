package com.example.eventreminder.card_o.theme

// region Imports
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import com.example.eventreminder.card_o.model.CardTheme
import timber.log.Timber
// endregion

// region Constants
private const val TAG = "ThemeEngine"
// endregion

/**
 * ThemeEngine
 *
 * Provides a central registry of available card themes.
 * Additional themes can easily be added in future TODO steps.
 */
object ThemeEngine {

    // region Theme Definitions

    private val momPinkDelight = CardTheme(
        backgroundColor = Color(0xFFFFE6F1),
        gradientOverlay = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFAACC),
                Color(0x00FFAACC)
            ),
            tileMode = TileMode.Clamp
        ),
        accentColor = Color(0xFFD81B60),
        secondaryAccentColor = Color(0xFFFFCDD2),
        themeName = "Mom Pink Delight"
    )

    private val elegantBlue = CardTheme(
        backgroundColor = Color(0xFFE3F2FD),
        gradientOverlay = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF90CAF9),
                Color(0x0090CAF9)
            )
        ),
        accentColor = Color(0xFF0D47A1),
        secondaryAccentColor = Color(0xFF64B5F6),
        themeName = "Elegant Blue"
    )

    private val vibrantSunset = CardTheme(
        backgroundColor = Color(0xFFFFF3E0),
        gradientOverlay = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFF7043),
                Color(0xFFFFA726),
                Color(0x00FFA726)
            )
        ),
        accentColor = Color(0xFFE65100),
        secondaryAccentColor = Color(0xFFFFCC80),
        themeName = "Vibrant Sunset"
    )

    // endregion

    // region Registry

    /** List of all selectable card themes */
    val allThemes: List<CardTheme> = listOf(
        momPinkDelight,
        elegantBlue,
        vibrantSunset
    )

    /**
     * Return the next theme after the given index,
     * wrapping to the beginning when needed.
     */
    fun nextTheme(currentIndex: Int): Pair<Int, CardTheme> {
        val newIndex = (currentIndex + 1) % allThemes.size
        val theme = allThemes[newIndex]
        Timber.tag(TAG).d("Theme selected: ${theme.themeName}")
        return newIndex to theme
    }

    /**
     * Return the default theme.
     */
    fun defaultTheme(): CardTheme = momPinkDelight

    // endregion
}
