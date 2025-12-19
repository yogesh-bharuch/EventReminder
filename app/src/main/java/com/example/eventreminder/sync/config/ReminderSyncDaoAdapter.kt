package com.example.eventreminder.sync.config

import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.sync.core.SyncDaoAdapter
import timber.log.Timber
import com.example.eventreminder.logging.DELETE_TAG

/**
 * Adapter so SyncEngine can talk to Room generically.
 * UUID-only version.
 */
class ReminderSyncDaoAdapter(
    private val dao: ReminderDao
) : SyncDaoAdapter<EventReminder> {

    /**
     * Return local items whose updatedAt > updatedAfter.
     * Includes soft-deleted items (tombstones).
     */
    override suspend fun getLocalsChangedAfter(updatedAfter: Long?): List<EventReminder> {
        val all = dao.getAllIncludingDeletedOnce()
        return if (updatedAfter == null) all else all.filter { it.updatedAt > updatedAfter }
    }

    /**
     * Insert or update list of reminders.
     */
    override suspend fun upsertAll(items: List<EventReminder>) {
        dao.insertAll(items)
    }

    /**
     * Apply REMOTE tombstones locally.
     *
     * IMPORTANT:
     * - Must NOT bump updatedAt
     * - Must NOT resurrect anything
     */
    override suspend fun markDeletedByIds(ids: List<String>) {
        ids.forEach { id ->
            Timber.tag(DELETE_TAG).w("REMOTE TOMBSTONE → apply locally id=$id")
            dao.markDeletedRemote(id)
        }
    }

    /**
     * Used for conflict resolution.
     * Returns local updatedAt for given UUID, or null if row missing.
     */
    override suspend fun getLocalUpdatedAt(id: String): Long? {
        return dao.getUpdatedAt(id)
    }

    /**
     * 🔥 CRITICAL: Single source of truth for local tombstone state
     *
     * Returns true IFF:
     *   Room row exists AND isDeleted = true
     */
    override suspend fun isLocalDeleted(id: String): Boolean {
        val deleted = dao.isDeleted(id) ?: false
        Timber.tag(DELETE_TAG).d("isLocalDeleted(id=$id) -> $deleted")
        return deleted
    }
}
