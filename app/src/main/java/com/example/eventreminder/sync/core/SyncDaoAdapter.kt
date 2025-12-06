package com.example.eventreminder.sync.core

/**
 * Generic abstraction over Room DAO so the sync engine remains
 * app-agnostic and works with any entity type.
 */
interface SyncDaoAdapter<Local> {

    /**
     * Fetch all local rows where updatedAt > updatedAfter.
     * Used during Local → Remote sync.
     */
    suspend fun getLocalsChangedAfter(updatedAfter: Long?): List<Local>

    /**
     * Insert or update the given list of entities in Room.
     * Used when applying remote changes.
     */
    suspend fun upsertAll(items: List<Local>)

    /**
     * Mark entities as deleted by ID in Room.
     * Used when remote Firestore documents indicate deletion.
     */
    suspend fun markDeletedByIds(ids: List<String>)
}
