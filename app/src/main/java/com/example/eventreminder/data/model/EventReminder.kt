package com.example.eventreminder.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * EventReminder - now supports **multiple reminder offsets**.
 *
 * eventEpochMillis:
 *      UTC epoch millis for the event time (chosen by user in their local zone).
 *
 * timeZone:
 *      IANA zone ID used when selecting date/time ("Asia/Kolkata").
 *
 * repeatRule:
 *      null | "every_minute" | "daily" | "weekly" | "monthly" | "yearly"
 *
 * reminderOffsets:
 *      List of offsets (millis) for multi-reminder support.
 *      Example: [0L, 3600000L, 86400000L] → at time, 1 hour before, 1 day before.
 *
 * enabled:
 *      Whether the reminder is active.
 */

@Entity(tableName = "reminders")
data class EventReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,

    val title: String,
    val description: String? = null,

    val eventEpochMillis: Long,
    val timeZone: String,

    val repeatRule: String? = null,

    // 🆕 Multiple reminder offsets (replace old reminderOffsetMillis)
    val reminderOffsets: List<Long> = listOf(0L),

    val enabled: Boolean = true
)
