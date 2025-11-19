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
 * A single alarm entry inside the report
 */
data class AlarmEntry(
    val eventId: Long,
    val eventTitle: String,

    // MUST be epoch millis (UTC) for PDF date formatting
    val nextTrigger: Long,

    // MUST be Long for calculations (days, hours, mins)
    val offsetMinutes: Long,
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


/*
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
*/
