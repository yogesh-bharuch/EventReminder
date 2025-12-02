package com.example.eventreminder.cards

import com.example.eventreminder.cards.model.CardData

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardUiState"

/**
 * CardUiState
 *
 * Standard sealed-state for card generator UI.
 * Used directly by CardViewModel and CardDebugScreen.
 */
sealed class CardUiState {

    /** Still loading reminder from repository */
    object Loading : CardUiState()

    /** No reminderId provided — show placeholder card */
    object Placeholder : CardUiState()

    /** Database or calculation error */
    data class Error(val message: String) : CardUiState()

    /** Successfully computed CardData */
    data class Data(val cardData: CardData) : CardUiState()
}
