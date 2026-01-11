package com.example.eventreminder.pdf

import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.sync.core.UserIdProvider
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject


class ReminderListReportBuilder @Inject constructor(
    private val reminderDao: ReminderDao,
    private val userIdProvider: UserIdProvider
) {

    /**
     * Caller:
     *  - PdfViewModel.runReminderListReport()
     *
     * Responsibility:
     *  - Builds a reminder-based PDF report.
     *  - Uses ONLY actual event date & time (no offsets).
     *  - One row per reminder.
     *  - Groups reminders by title (A → Z).
     *
     * Output:
     *  - ReminderListReport
     */
    suspend fun buildReport(): ReminderListReport {

        val uid = userIdProvider.getUserId()
            ?: error("❌ UID is null — cannot build reminder list report")

        val reminders = reminderDao
            .getAllOnce(uid = uid)
            .filter { it.enabled && !it.isDeleted }

        val grouped = reminders
            .groupBy { it.title }
            .toSortedMap()
            .map { (title, list) ->
                ReminderTitleSection(
                    title = title,
                    reminders = list
                        .sortedBy { it.eventEpochMillis }
                        .map { reminder ->
                            ReminderListRow(
                                shortId = shortenId(reminder.id),
                                description = reminder.description ?: "-",
                                eventDateTime = formatEventDateTime(
                                    reminder.eventEpochMillis,
                                    reminder.timeZone
                                )
                            )
                        }
                )
            }

        return ReminderListReport(
            groupedByTitle = grouped,
            generatedAt = LocalDateTime.now()
        )
    }

    // ---------------------------------------------------------
    // Helpers (presentation-only)
    // ---------------------------------------------------------
    private fun shortenId(id: String): String {
        return if (id.length <= 4) id
        else "${id.take(2)}..${id.takeLast(2)}"
    }

    private fun formatEventDateTime(
        epochMillis: Long,
        zoneId: String
    ): String {
        val zoned = Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.of(zoneId))

        return zoned.format(
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
        )
    }
}
