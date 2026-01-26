package com.example.eventreminder.sync.core

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for retrieving and updating sync checkpoints.
 */
@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncMetadataEntity)
}

/*
* ✅ 7. SyncMetadataDao.kt
Room DAO to read/write sync metadata.
Used by SyncEngine to update checkpoints.
👉 Keeps track of where last sync left off.
* */