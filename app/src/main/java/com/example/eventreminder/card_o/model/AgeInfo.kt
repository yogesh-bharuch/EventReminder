package com.example.eventreminder.card.model

// region Imports
import java.time.LocalDate
import java.time.Period
import timber.log.Timber
// endregion

// region Constants
private const val TAG = "AgeInfo"
// endregion

/**
 * AgeInfo
 *
 * Represents age or anniversary duration.
 * Auto-calculates the number of years and a friendly message.
 */
data class AgeInfo(

    // Date of birth or anniversary date
    val eventDate: LocalDate,

    // Today's date (for calculation)
    val today: LocalDate = LocalDate.now()
) {

    // region Computed Properties

    /** Years difference between eventDate and today. */
    val years: Int = Period.between(eventDate, today).years

    /** Friendly message (example: "Turns 32 Today!", "5th Anniversary"). */
    val friendlyMessage: String =
        when {
            years <= 0 -> "Special Day!"
            years == 1 -> "1 Year Celebration!"
            else -> "$years Years Celebration!"
        }
    // endregion

    init {
        Timber.tag(TAG).d("AgeInfo created: years=$years message=$friendlyMessage")
    }
}
