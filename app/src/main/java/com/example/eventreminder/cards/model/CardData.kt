package com.example.eventreminder.cards.model

import java.time.ZoneId

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardData"

/**
 * CardData
 *
 * Pure UI model representing exactly what the CardPreview
 * composable requires for rendering.
 *
 * Produced by CardViewModel after transforming EventReminder.
 */
data class CardData(
    val reminderId: Long,
    val title: String,
    val name: String?,
    val eventKind: EventKind = EventKind.GENERIC,
    val ageOrYearsLabel: String? = null,
    val originalDateLabel: String,
    val nextDateLabel: String,
    val timezone: ZoneId = ZoneId.systemDefault(),
    // ⭐ NEW: list of stickers to draw on card
    val stickers: List<CardSticker> = emptyList()
)

/**
 * EventKind
 *
 * Classification for card templates.
 * - BIRTHDAY and ANNIVERSARY use age/years UI.
 * - GENERIC uses minimal layout.
 */
enum class EventKind {
    BIRTHDAY,
    ANNIVERSARY,
    GENERIC
}
