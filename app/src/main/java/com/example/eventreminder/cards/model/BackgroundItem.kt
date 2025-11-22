package com.example.eventreminder.cards.model

import androidx.annotation.DrawableRes

/**
 * Represents one selectable background thumbnail in the BackgroundBar.
 */
data class BackgroundItem(
    val name: String,
    @DrawableRes val resId: Int
)
