package com.example.eventreminder.sync.config

import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.sync.core.SyncDaoAdapter

/**
 * Adapter so SyncEngine can talk to Room generically.
 */
class ReminderSyncDaoAdapter(
    private val dao: ReminderDao
) : SyncDaoAdapter<EventReminder> {

    /**
     * Return local items whose updatedAt is > updatedAfter.
     * This automatically includes soft-deleted items because the delete
     * operation updates updatedAt in Room.
     */
    override suspend fun getLocalsChangedAfter(updatedAfter: Long?): List<EventReminder> {
        val all = dao.getAllIncludingDeletedOnce()
        return if (updatedAfter == null) all else all.filter { it.updatedAt > updatedAfter }
    }

    override suspend fun upsertAll(items: List<EventReminder>) {
        dao.insertAll(items)
    }

    override suspend fun markDeletedByIds(ids: List<String>) {
        ids.forEach { strId ->
            strId.toLongOrNull()?.let { dao.markDeleted(it) }
        }
    }

    /** Used by SyncEngine for LATEST_UPDATED_WINS conflict resolution. */
    override suspend fun getLocalUpdatedAt(id: String): Long? {
        val numericId = id.toLongOrNull() ?: return null
        return dao.getUpdatedAt(numericId)
    }
}

/*
*✅ 4. ReminderSyncDaoAdapter.kt
Implements SyncDaoAdapter for EventReminder.
Provides:
Room queries for changed reminders
Insert/update reminders
Soft-delete local reminders
Fetch local updatedAt for conflict logic
👉 This makes ReminderDao usable by the SyncEngine.
* */