package com.example.eventreminder.sync.core

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(

    @PrimaryKey val key: String,
    val lastLocalSyncAt: Long?,
    val lastRemoteSyncAt: Long?
)

/*
* ✅ 6. SyncMetadataEntity.kt
Room table that stores the sync checkpoints.
Fields:
lastLocalSyncAt
lastRemoteSyncAt
👉 Helps SyncEngine do incremental sync instead of full sync.
* */