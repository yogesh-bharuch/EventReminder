package com.example.eventreminder.ui.viewmodels

// =============================================================
// Imports
// =============================================================
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * EventReminderUI
 * -----------------
 * UI-ready model used by EventCard and EventsListGrouped.
 *
 * NOW updated to use String UUID for reminder ID.
 */
data class EventReminderUI(
    val id: String,                     // <-- UUID
    val title: String,
    val description: String?,
    val eventEpochMillis: Long,
    val repeatRule: String?,
    val timeRemainingLabel: String,
    val formattedDateLabel: String
) {

    companion object {

        private val dateFormatter =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

        fun from(
            id: String,                 // <-- UUID
            title: String,
            desc: String?,
            eventMillis: Long,
            repeat: String?,
            tz: String,
            offsets: List<Long>
        ): EventReminderUI {

            val zone = ZoneId.of(tz)
            val zdt = Instant.ofEpochMilli(eventMillis).atZone(zone)
            val dateLabel = zdt.format(dateFormatter)

            val remainingLabels =
                offsets.sortedDescending().map { offMillis ->
                    val triggerEpoch = eventMillis - offMillis
                    formatRemaining(triggerEpoch)
                }

            val combinedRemaining = remainingLabels.joinToString(", ")

            return EventReminderUI(
                id = id,
                title = title,
                description = desc,
                eventEpochMillis = eventMillis,
                repeatRule = repeat,
                formattedDateLabel = dateLabel,
                timeRemainingLabel = combinedRemaining
            )
        }

        private fun formatRemaining(triggerMillis: Long): String {
            val now = Instant.now().toEpochMilli()
            val diff = triggerMillis - now

            return when {
                diff < 0 -> "Elapsed"
                diff < 60_000 -> "In a few seconds"
                diff < 3_600_000 -> "In ${diff / 60_000} min"
                diff < 86_400_000 -> "In ${diff / 3_600_000} hours"
                diff < 172_800_000 -> "Tomorrow"
                diff < 604_800_000 -> "In ${diff / 86_400_000} days"
                else -> {
                    val z = Instant.ofEpochMilli(triggerMillis)
                        .atZone(ZoneId.systemDefault())
                    val fmt = DateTimeFormatter.ofPattern("dd MMM yyyy")
                    "On ${z.format(fmt)}"
                }
            }
        }
    }
}
