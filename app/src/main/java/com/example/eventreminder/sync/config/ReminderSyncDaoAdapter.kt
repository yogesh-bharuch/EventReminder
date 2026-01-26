package com.example.eventreminder.sync.config

// =============================================================
// Imports
// =============================================================
import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.logging.DELETE_TAG
import com.example.eventreminder.sync.core.SyncDaoAdapter
import com.example.eventreminder.sync.core.UserIdProvider
import timber.log.Timber

/**
 * ReminderSyncDaoAdapter
 *
 * Adapter so SyncEngine can talk to Room generically.
 *
 * UID-scoped, UUID-only version.
 *
 * Rules:
 * - UID is resolved via UserIdProvider
 * - Fail fast if UID is null
 * - Tombstone semantics are preserved
 * - NO SyncEngine redesign
 */
class ReminderSyncDaoAdapter(
    private val dao: ReminderDao,
    private val userIdProvider: UserIdProvider
) : SyncDaoAdapter<EventReminder> {

    // ---------------------------------------------------------
    // UID helper
    // ---------------------------------------------------------
    private suspend fun requireUid(): String {
        return userIdProvider.getUserId()
            ?: error("❌ UID is null — SyncEngine invoked without authenticated user")
    }

    /**
     * Return local items whose updatedAt > updatedAfter.
     * Includes soft-deleted items (tombstones).
     */
    override suspend fun getLocalsChangedAfter(updatedAfter: Long?): List<EventReminder> {
        val uid = requireUid()
        val all = dao.getAllIncludingDeletedOnce(uid = uid)
        return if (updatedAfter == null) {
            all
        } else {
            all.filter { it.updatedAt > updatedAfter }
        }
    }

    /**
     * Insert or update list of reminders.
     *
     * UID is enforced before writing.
     */
    override suspend fun upsertAll(items: List<EventReminder>) {
        val uid = requireUid()

        val stamped = items.map { it.copy(uid = uid) }
        dao.insertAll(stamped)
    }

    /**
     * Apply REMOTE tombstones locally.
     *
     * IMPORTANT:
     * - Must NOT bump updatedAt
     * - Must NOT resurrect anything
     */
    override suspend fun markDeletedByIds(ids: List<String>) {
        val uid = requireUid()

        ids.forEach { id ->
            Timber.tag(DELETE_TAG).w("REMOTE TOMBSTONE → apply locally uid=$uid id=$id")
            dao.markDeletedRemote(uid = uid, id = id)
        }
    }

    /**
     * Used for conflict resolution.
     * Returns local updatedAt for given UUID, or null if row missing.
     */
    override suspend fun getLocalUpdatedAt(id: String): Long? {
        return dao.getUpdatedAt(
            uid = requireUid(),
            id = id
        )
    }

    /**
     * 🔥 CRITICAL: Single source of truth for local tombstone state
     *
     * Returns true IFF:
     *   Room row exists AND isDeleted = true
     */
    override suspend fun isLocalDeleted(id: String): Boolean {
        val deleted = dao.isDeleted(
            uid = requireUid(),
            id = id
        ) ?: false

        Timber.tag(DELETE_TAG).d("isLocalDeleted(uid=?, id=$id) -> $deleted")
        return deleted
    }
}
