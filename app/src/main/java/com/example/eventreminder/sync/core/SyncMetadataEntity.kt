package com.example.eventreminder.sync.core

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores sync checkpoints for each entity type.
 * Key identifies which entity table (e.g., "reminders").
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(

    @PrimaryKey val key: String,

    /** Last local updatedAt value that was successfully pushed. */
    val lastLocalSyncAt: Long?,

    /** Last remote updatedAt value successfully pulled. */
    val lastRemoteSyncAt: Long?
)
