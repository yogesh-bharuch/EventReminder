package com.example.eventreminder.data.model

import androidx.room.Entity

/**
 * ReminderFireStateEntity
 *
 * Tracks the last-fired timestamp for a given reminderId + offset pair.
 * This allows precise missed-detection after reboot and prevents duplicates.
 */
@Entity(
    tableName = "reminder_fire_state",
    primaryKeys = ["reminderId", "offsetMillis"]
)
data class ReminderFireStateEntity(
    val reminderId: String,
    val offsetMillis: Long,
    val lastFiredAt: Long? = null
)
