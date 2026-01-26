package com.example.eventreminder.data.model

import androidx.room.Entity

/**
 * ReminderFireStateEntity
 *
 * Tracks per-offset lifecycle events for a reminder.
 *
 * - lastFiredAt  → when the notification was shown
 * - dismissedAt  → when the user manually dismissed the notification
 *
 * Fire ≠ Dismiss
 * Both are tracked independently for correct lifecycle handling.
 *
 * NOTE:
 * One row per reminder occurrence (offset=0).
 * Pre-offset notifications do NOT create fire-state rows.
 */
@Entity(
    tableName = "reminder_fire_state",
    primaryKeys = ["reminderId", "offsetMillis"]
)
data class ReminderFireStateEntity(
    val reminderId: String,
    val offsetMillis: Long,
    val lastFiredAt: Long? = null,
    val dismissedAt: Long? = null
)
