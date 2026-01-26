package com.example.eventreminder.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Local Room entity scoped to a Firebase user.
 *
 * Key guarantees:
 * - Each reminder belongs to exactly one Firebase UID
 * - Tombstone semantics remain unchanged
 * - Safe for multi-user on the same device
 */
@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["uid"]),                     // fast per-user filtering
        Index(value = ["uid", "id"], unique = true) // prevent cross-user UUID collision
    ]
)
@Serializable
data class EventReminder(

    val uid: String,
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val eventEpochMillis: Long,
    val timeZone: String,
    val repeatRule: String? = null,
    val reminderOffsets: List<Long> = listOf(0L),
    val enabled: Boolean = true,
    val isDeleted: Boolean = false,
    /**
     * Optional background image URI for pixel cards.
     */
    val backgroundUri: String? = null,
    /**
     * Last modified timestamp.
     * Used for sync conflict resolution.
     */
    val updatedAt: Long = System.currentTimeMillis()
)
