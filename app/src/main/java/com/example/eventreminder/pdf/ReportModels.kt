package com.example.eventreminder.pdf

import java.time.LocalDateTime

/**
 * =============================================================
 * PDF REPORT MODELS (TODO-1)
 * =============================================================
 */

/**
 * A single alarm entry inside the report
 */
data class AlarmEntry(
    val eventId: Long,
    val eventTitle: String,
    val nextTrigger: LocalDateTime,
    val offsetMinutes: Int,
)

/**
 * A section grouped by title on page 1
 */
data class TitleSection(
    val title: String,
    val alarms: List<AlarmEntry>
)

/**
 * Full report data used by the PDF builder
 */
data class ActiveAlarmReport(
    val groupedByTitle: List<TitleSection>,
    val sortedAlarms: List<AlarmEntry>,   // soonest first
    val generatedAt: LocalDateTime
)
