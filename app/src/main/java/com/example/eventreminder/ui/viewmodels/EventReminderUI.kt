package com.example.eventreminder.ui.viewmodels

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// //👉 In short: it converts raw event data into a user‑friendly reminder object with readable date and remaining time labels for display in the UI.
// UI model for event reminders. used in ui event card
data class EventReminderUI(
    val id: Long,
    val title: String,
    val description: String?,
    val eventEpochMillis: Long,
    val repeatRule: String?,
    val timeRemainingLabel: String,
    val formattedDateLabel: String
) {
    //- from(...): factory method that builds an EventReminderUI from raw values.
    companion object {

        private val dateFormatter =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

        fun from(
            id: Long,
            title: String,
            desc: String?,
            eventMillis: Long,
            repeat: String?,
            tz: String
        ): EventReminderUI {

            val zone = ZoneId.of(tz)
            val zdt = Instant.ofEpochMilli(eventMillis).atZone(zone)

            // Display date
            val dateLabel = zdt.format(dateFormatter)

            // Time remaining (simple)
            val remaining = formatRemaining(eventMillis)

            return EventReminderUI(
                id = id,
                title = title,
                description = desc,
                eventEpochMillis = eventMillis,
                repeatRule = repeat,
                formattedDateLabel = dateLabel,
                timeRemainingLabel = remaining
            )
        }
        // - formatRemaining(...): helper that compares event time with Instant.now() and returns a relative label based on the difference.
        private fun formatRemaining(eventMillis: Long): String {
            val now = Instant.now().toEpochMilli()
            val diff = eventMillis - now

            return when {
                diff < 0 -> "Elapsed"
                diff < 60_000 -> "In a few seconds"
                diff < 3_600_000 -> "In ${diff / 60_000} min"
                diff < 86_400_000 -> "In ${diff / 3_600_000} hours"
                diff < 172_800_000 -> "Tomorrow"
                diff < 604_800_000 -> "In ${diff / 86_400_000} days"
                else -> {
                    val z = Instant.ofEpochMilli(eventMillis)
                        .atZone(ZoneId.systemDefault())
                    val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy")
                    "On ${z.format(fmt)}"
                }
            }
        }
    }
}
