package com.example.eventreminder.card.model

// region Imports
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import timber.log.Timber
// endregion

// region Constants
private const val TAG = "CardRenderRequest"
// endregion

/**
 * CardRenderRequest
 *
 * Master container for rendering a birthday/anniversary card.
 * The CardComposer engine consumes this to generate the final bitmap.
 */
data class CardRenderRequest(

    // Canvas size (default square for social media)
    val width: Int = 1080,
    val height: Int = 1080,

    // Theme defining background, gradient, accents
    val theme: CardTheme,

    // Name of recipient (“Mom”, “Riya Sharma”)
    val recipientName: String,

    // Main greeting message (“Happy Birthday”, etc.)
    val message: String,

    // Auto-calculated age / anniversary info
    val ageInfo: AgeInfo? = null,

    // Relationship icon (optional)
    val relationIcon: RelationIcon? = null,

    // Optional user photo layer
    val photoLayer: CardPhotoLayer? = null,

    // Stickers (confetti, balloons, cake icon)
    val stickers: List<CardSticker> = emptyList(),

    // Text blocks (title, message, quotes, footer)
    val textBlocks: List<CardTextBlock> = emptyList(),

    // App brand watermark (“Created with EventReminder”)
    val watermarkText: String? = "Created with Event Reminder"
) {

    init {
        Timber.tag(TAG).d(
            "RenderRequest created: recipient=$recipientName, theme=${theme.themeName}, textBlocks=${textBlocks.size}, stickers=${stickers.size}"
        )
    }
}
