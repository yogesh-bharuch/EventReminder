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
 * Converts raw persisted reminder values into:
 *  - Clean readable date labels
 *  - Clean readable remaining-time labels
 *  - Supports multi-offset remaining times (ex: "In 3 hours, In 6 hours")
 *
 * NOTE:
 * This model is intentionally independent of Room and domain logic.
 * Only UI-oriented formatting should happen here.
 */
data class EventReminderUI(
    val id: Long,
    val title: String,
    val description: String?,
    val eventEpochMillis: Long,
    val repeatRule: String?,              // raw repeat key (for chips)
    val timeRemainingLabel: String,       // "In 3 hours, In 6 hours"
    val formattedDateLabel: String        // "04 Dec 2025, 11:00 AM"
) {

    companion object {

        // Formatter for displaying event date/time
        private val dateFormatter =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

        /**
         * Factory method to create a UI model from raw reminder data.
         *
         * @param id         Reminder ID
         * @param title      Title
         * @param desc       Description (nullable)
         * @param eventMillis Next event occurrence epoch time
         * @param repeat     Repeat rule key (daily, weekly, etc.)
         * @param tz         Stored timezone
         * @param offsets    Reminder offsets in millis (0, 5min, 3hr, etc.)
         */
        fun from(
            id: Long,
            title: String,
            desc: String?,
            eventMillis: Long,
            repeat: String?,
            tz: String,
            offsets: List<Long>
        ): EventReminderUI {

            // ------------------------------------------------------------
            // 1) Format main event date (next occurrence after repeat logic)
            // ------------------------------------------------------------
            val zone = ZoneId.of(tz)
            val zdt = Instant.ofEpochMilli(eventMillis).atZone(zone)
            val dateLabel = zdt.format(dateFormatter)

            // ------------------------------------------------------------
            // 2) Compute remaining time for EACH offset
            //
            // Example:
            //   Event = 11:00
            //   Offsets = [0, 3 hours]
            //   → Times = [11:00, 08:00]
            //   → Remaining = ["In 3 hours", "In 6 hours"]
            //
            // Sorted so earliest trigger comes first.
            // ------------------------------------------------------------
            val remainingLabels: List<String> =
                offsets.sortedDescending().map { offMillis ->

                    // actual trigger time = eventTime - offset
                    val triggerEpoch = eventMillis - offMillis

                    formatRemaining(triggerEpoch)
                }

            // Join remaining labels into clean comma-separated string
            val combinedRemaining = remainingLabels.joinToString(", ")

            // ------------------------------------------------------------
            // 3) Produce the UI model
            // ------------------------------------------------------------
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

        /**
         * Converts a single trigger time (event - offset) into a
         * relative human-readable label.
         *
         * @param triggerMillis actual alarm trigger time in epoch millis
         *
         * Examples:
         *   - "In a few seconds"
         *   - "In 12 min"
         *   - "In 3 hours"
         *   - "Tomorrow"
         *   - "In 5 days"
         *   - "On 05 Jan 2026"
         */
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
