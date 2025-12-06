package com.example.eventreminder.sync.config

// =============================================================
// Imports
// =============================================================
import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.sync.core.SyncDaoAdapter

/**
 * ReminderSyncDaoAdapter
 *
 * Thin wrapper around ReminderDao so the generic SyncEngine
 * can push/pull EventReminder items using SyncDaoAdapter API.
 *
 * NOTE:
 * - EventReminder currently has no "isDeleted" column.
 *   Deletion sync will be added later (v2).
 */
class ReminderSyncDaoAdapter(
    private val dao: ReminderDao
) : SyncDaoAdapter<EventReminder> {

    /**
     * Return reminders where updatedAt > updatedAfter.
     *
     * For v1, we use eventEpochMillis as updatedAt.
     */
    override suspend fun getLocalsChangedAfter(updatedAfter: Long?): List<EventReminder> {
        // Room does not have a direct query for updatedAt > X,
        // so for now we fetch everything and filter in-memory.
        // (This is fine for typical small to medium dataset sizes.)
        val all = dao.getAllOnce()
        return if (updatedAfter == null) {
            all   // initial sync → treat all data as new
        } else {
            all.filter { it.eventEpochMillis > updatedAfter }
        }
    }

    /**
     * Insert or update the given reminders.
     */
    override suspend fun upsertAll(items: List<EventReminder>) {
        dao.insertAll(items)
    }

    /**
     * Delete reminders by ID.
     * (For v1 we have no isDeleted column, so remote tombstone = physical delete.)
     */
    override suspend fun markDeletedByIds(ids: List<String>) {
        ids.forEach { strId ->
            strId.toLongOrNull()?.let { dao.deleteById(it) }
        }
    }
}
