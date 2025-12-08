package com.example.eventreminder.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * EventReminder (UUID-based)
 *
 * Fresh schema — UUID is the only primary key.
 * No legacy numeric ID is kept.
 */
@Entity(tableName = "reminders")
@Serializable
data class EventReminder(

    // UUID primary key
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val title: String,
    val description: String? = null,
    val eventEpochMillis: Long,
    val timeZone: String,
    val repeatRule: String? = null,
    val reminderOffsets: List<Long> = listOf(0L),
    val enabled: Boolean = true,

    // Optional background image URI for pixel cards
    val backgroundUri: String? = null,

    // Soft delete flag
    val isDeleted: Boolean = false,

    // Last modified timestamp
    val updatedAt: Long = System.currentTimeMillis()
)
