package com.example.eventreminder.card.model

// region Imports
import androidx.annotation.DrawableRes
import timber.log.Timber
// endregion

// region Constants
private const val TAG = "RelationIcon"
// endregion

/**
 * RelationIcon
 *
 * Represents a small icon showing the recipient's relationship to the user.
 * Example: 👩‍🦰 Mom, 👨‍🦳 Dad, ❤️ Wife, 🎉 Friend, etc.
 */
data class RelationIcon(

    // Relationship text (example: "Mother", "Best Friend")
    val relationName: String,

    // Drawable resource for icon
    @DrawableRes val drawableResId: Int
) {
    init {
        Timber.tag(TAG).d("RelationIcon created: $relationName (res=$drawableResId)")
    }
}
