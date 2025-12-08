package com.example.eventreminder.sync.config

import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.sync.core.SyncDaoAdapter

/**
 * Adapter so SyncEngine can talk to Room generically.
 * UUID-only version.
 */
class ReminderSyncDaoAdapter(
    private val dao: ReminderDao
) : SyncDaoAdapter<EventReminder> {

    /**
     * Return local items whose updatedAt > updatedAfter.
     * Includes soft-deleted items.
     */
    override suspend fun getLocalsChangedAfter(updatedAfter: Long?): List<EventReminder> {
        val all = dao.getAllIncludingDeletedOnce()   // DAO must now return UUID-based EventReminder
        return if (updatedAfter == null) all else all.filter { it.updatedAt > updatedAfter }
    }

    /**
     * Insert or update list of reminders.
     */
    override suspend fun upsertAll(items: List<EventReminder>) {
        dao.insertAll(items)  // DAO must support UUID primary key
    }

    /**
     * Soft-delete local reminders by UUID STRING.
     */
    override suspend fun markDeletedByIds(ids: List<String>) {
        ids.forEach { idString ->
            dao.markDeleted(idString)   // UUID string — NO conversion
        }
    }

    /**
     * Used for LATEST_UPDATED_WINS conflict resolution.
     * Returns local updatedAt for the given UUID.
     */
    override suspend fun getLocalUpdatedAt(id: String): Long? {
        return dao.getUpdatedAt(id)     // UUID string — NO conversion
    }
}
