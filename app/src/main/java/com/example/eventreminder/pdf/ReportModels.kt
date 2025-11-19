package com.example.eventreminder.pdf

import java.time.LocalDateTime

/**
 * =============================================================
 * PDF REPORT MODELS (Updated for TODO-2.1)
 *
 * All values now match the generator’s expectations:
 *  - nextTrigger: Long (epoch millis)
 *  - offsetMinutes: Long
 * =============================================================
 */

/**
 * A single alarm entry inside the report.
 * Now includes:
 *  - eventDateEpoch → original event date/time (epoch millis)
 */
data class AlarmEntry(
    val eventId: Long,
    val eventTitle: String,

    // Original event date/time set by the user
    val eventDateEpoch: Long,

    // Trigger after applying offset
    val nextTrigger: Long,

    // Offset in minutes (positive minutes before)
    val offsetMinutes: Long,
    val description: String? = null
)

/**
 * A section grouped by title on page 1
 */
data class TitleSection(
    val title: String,
    val alarms: List<AlarmEntry>
)

/**
 * The complete report passed into PdfTodo2Generator
 */
data class ActiveAlarmReport(
    val groupedByTitle: List<TitleSection>,
    val sortedAlarms: List<AlarmEntry>,  // soonest first
    val generatedAt: LocalDateTime       // for header/footer timestamp
)
