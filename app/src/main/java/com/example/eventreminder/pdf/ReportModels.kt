package com.example.eventreminder.pdf

import java.time.LocalDateTime

/**
 * =============================================================
 * PDF REPORT MODELS
 *
 * This file contains all immutable data models passed
 * into PdfGenerator for rendering.
 *
 * Models are presentation-only and MUST NOT contain logic.
 * =============================================================
 */

/**
 * A single alarm entry inside the ACTIVE ALARM report.
 *
 * Represents one scheduled firing (after offset).
 */
data class AlarmEntry(
    val eventId: String,
    val eventTitle: String,

    // Original event date/time set by the user (epoch millis)
    val eventDateEpoch: Long,

    // Actual trigger time after applying offset (epoch millis)
    val nextTrigger: Long,

    // Offset in minutes (before event)
    val offsetMinutes: Long,

    val description: String? = null
)

/**
 * Grouped alarm section (page-1 of alarm PDF).
 */
data class TitleSection(
    val title: String,
    val alarms: List<AlarmEntry>
)

/**
 * Root model for ACTIVE ALARM PDF.
 */
data class ActiveAlarmReport(
    val groupedByTitle: List<TitleSection>,
    val sortedAlarms: List<AlarmEntry>,
    val generatedAt: LocalDateTime
)

/**
 * =============================================================
 * REMINDER LIST REPORT MODELS (NO OFFSETS)
 * =============================================================
 */

/**
 * One reminder row (one per reminder, no expansion).
 */
data class ReminderListRow(
    val shortId: String,
    val description: String,
    val eventDateTime: String
)

/**
 * Group of reminders by title.
 */
data class ReminderTitleSection(
    val title: String,
    val reminders: List<ReminderListRow>
)

/**
 * Root model for REMINDER LIST PDF.
 */
data class ReminderListReport(
    val groupedByTitle: List<ReminderTitleSection>,
    val generatedAt: LocalDateTime
)
