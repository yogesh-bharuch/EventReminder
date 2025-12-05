package com.example.eventreminder.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable


/**
 * EventReminder
 *
 * Supports multi-offset reminders, repeating rules, UTC storage, and enabled state.
 */
@Entity(tableName = "reminders")
@Serializable
data class EventReminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    val title: String,
    val description: String? = null,
    val eventEpochMillis: Long,
    val timeZone: String,
    val repeatRule: String? = null,
    val reminderOffsets: List<Long> = listOf(0L),
    val enabled: Boolean = true,
    val backgroundUri: String? = null   // file path or content URI
)
