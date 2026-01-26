package com.example.eventreminder.sync.core

/**
 * DAO adapter used by SyncEngine for Room operations.
 */
interface SyncDaoAdapter<Local : Any> {

    /**
     * Return all local items where updatedAt > updatedAfter.
     */
    suspend fun getLocalsChangedAfter(updatedAfter: Long?): List<Local>

    /**
     * Insert or update a list of items.
     */
    suspend fun upsertAll(items: List<Local>)

    /**
     * Mark items as deleted (tombstone).
     */
    suspend fun markDeletedByIds(ids: List<String>)

    /**
     * 🔥 NEW — Used in SyncEngine conflict resolution
     *
     * Must return the local.updatedAt for a given ID,
     * OR null if the local row does not exist.
     */
    suspend fun getLocalUpdatedAt(id: String): Long?

    suspend fun isLocalDeleted(id: String): Boolean


}

/*
*✅ 3. SyncDaoAdapter.kt
Abstract interface for Room operations used by SyncEngine.
Declares:
getLocalsChangedAfter()
upsertAll()
markDeletedByIds()
getLocalUpdatedAt()
👉 This is a bridge between Room and SyncEngine (generic for any entity). */