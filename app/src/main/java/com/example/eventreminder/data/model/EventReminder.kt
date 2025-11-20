package com.example.eventreminder.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * EventReminder
 *
 * Supports multi-offset reminders, repeating rules, UTC storage, and enabled state.
 */
@Entity(tableName = "reminders")
data class EventReminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val title: String,
    val description: String? = null,

    val eventEpochMillis: Long,
    val timeZone: String,

    val repeatRule: String? = null,

    val reminderOffsets: List<Long> = listOf(0L),

    val enabled: Boolean = true
)
