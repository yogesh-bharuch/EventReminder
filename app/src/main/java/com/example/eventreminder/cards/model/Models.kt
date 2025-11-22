package com.example.eventreminder.cards.model

import androidx.annotation.DrawableRes
import java.util.*

/**
 * Small collection of model classes used by card UI and VM.
 */

/**
 * Represents one selectable background thumbnail in the BackgroundBar.
 * Option A model.
 */
data class BackgroundItem(
    val name: String,
    @DrawableRes val resId: Int
)

/**
 * CardBackground persisted reference stored on EventReminder.backgroundUri
 * id: string (we reuse cached path as id), resId optional for bundled backgrounds
 */
data class CardBackground(
    val id: String,
    val resId: Int = 0
)

/**
 * UI model provided to preview templates
 */
data class CardData(
    val reminderId: Long,
    val title: String,
    val name: String?,
    val eventKind: EventKind,
    val ageOrYearsLabel: String?,
    val originalDateLabel: String,
    val nextDateLabel: String,
    val timezone: java.time.ZoneId,
    val stickers: List<CardSticker> = emptyList()
)

/**
 * Sticker placed on card.
 * Coordinates are in dp-like float units for simple state handling.
 */
data class CardSticker(
    val id: String = UUID.randomUUID().toString(),
    @DrawableRes val drawableResId: Int? = null,
    val text: String? = null,
    var x: Float = 16f,
    var y: Float = 16f,
    var scale: Float = 1f,
    var rotation: Float = 0f
)

data class StickerPack(
    val id: String,
    val name: String,
    val items: List<StickerItem>
)

enum class EventKind { BIRTHDAY, ANNIVERSARY, GENERIC }
