package com.example.eventreminder.data.local

// ============================================================
// Imports
// ============================================================
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.eventreminder.data.model.EventReminder
import kotlinx.coroutines.flow.Flow

/**
 * ReminderDao
 *
 * UID-scoped DAO for EventReminder.
 *
 * Rules:
 * - Every query MUST include uid
 * - Prevents cross-user data leakage on same device
 * - Tombstone semantics are preserved
 * - SyncEngine behavior remains unchanged
 */
@Dao
interface ReminderDao {

    // ============================================================
    // INSERT / UPDATE
    // ============================================================

    /**
     * Inserts or replaces a reminder.
     * UID must already be populated on the entity.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: EventReminder)

    @Update
    suspend fun update(reminder: EventReminder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<EventReminder>)

    // ============================================================
    // READ (UID-SCOPED)
    // ============================================================

    /**
     * Live stream of non-deleted reminders for a user.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE uid = :uid
          AND isDeleted = 0
        ORDER BY updatedAt DESC
        """
    )
    fun getAll(uid: String): Flow<List<EventReminder>>

    /**
     * One-shot fetch of non-deleted reminders.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE uid = :uid
          AND isDeleted = 0
        """
    )
    suspend fun getAllOnce(uid: String): List<EventReminder>

    /**
     * One-shot fetch including tombstones.
     * Used by sync + cleanup pipelines.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE uid = :uid
        """
    )
    suspend fun getAllIncludingDeletedOnce(uid: String): List<EventReminder>

    /**
     * Fetch a reminder by UUID for a specific user.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE uid = :uid
          AND id = :id
        LIMIT 1
        """
    )
    suspend fun getById(uid: String, id: String): EventReminder?

    // ============================================================
    // DELETE (TOMBSTONE)
    // ============================================================

    /**
     * Local soft-delete.
     *
     * - Marks isDeleted = 1
     * - Updates updatedAt so change is pushed to remote
     */
    @Query(
        """
        UPDATE reminders
        SET isDeleted = 1,
            updatedAt = :timestamp
        WHERE uid = :uid
          AND id = :id
        """
    )
    suspend fun markDeleted(
        uid: String,
        id: String,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Remote tombstone application.
     *
     * - Marks isDeleted = 1
     * - DOES NOT update updatedAt
     *
     * Prevents re-triggering Local → Remote sync.
     */
    @Query(
        """
        UPDATE reminders
        SET isDeleted = 1
        WHERE uid = :uid
          AND id = :id
        """
    )
    suspend fun markDeletedRemote(uid: String, id: String)

    // ============================================================
    // TIMESTAMP HELPERS
    // ============================================================

    @Query(
        """
        UPDATE reminders
        SET updatedAt = :timestamp
        WHERE uid = :uid
          AND id = :id
        """
    )
    suspend fun updateUpdatedAt(uid: String, id: String, timestamp: Long)

    @Query(
        """
        SELECT updatedAt FROM reminders
        WHERE uid = :uid
          AND id = :id
        """
    )
    suspend fun getUpdatedAt(uid: String, id: String): Long?

    // ============================================================
    // ENABLE / DISABLE (LIFECYCLE)
    // ============================================================

    @Query(
        """
        UPDATE reminders
        SET enabled = :enabled,
            isDeleted = :isDeleted,
            updatedAt = :updatedAt
        WHERE uid = :uid
          AND id = :id
        """
    )
    suspend fun updateEnabled(
        uid: String,
        id: String,
        enabled: Boolean,
        isDeleted: Boolean,
        updatedAt: Long
    )

    // ============================================================
    // GARBAGE COLLECTION (TOMBSTONES)
    // ============================================================

    /**
     * Returns tombstones older than cutoff for a specific user.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE uid = :uid
          AND isDeleted = 1
          AND updatedAt <= :cutoffEpochMillis
        """
    )
    suspend fun getDeletedBefore(
        uid: String,
        cutoffEpochMillis: Long
    ): List<EventReminder>

    /**
     * Permanently deletes reminders by UUID for a user.
     *
     * ⚠️ Destructive — used only by manual tombstone GC.
     */
    @Query(
        """
        DELETE FROM reminders
        WHERE uid = :uid
          AND id IN (:ids)
        """
    )
    suspend fun hardDeleteByIds(uid: String, ids: List<String>)

    // ============================================================
    // NORMALIZATION
    // ============================================================

    /**
     * Normalize repeatRule: "" → NULL for consistency.
     * Applied per user.
     */
    @Query(
        """
        UPDATE reminders
        SET repeatRule = NULL
        WHERE uid = :uid
          AND repeatRule = ''
        """
    )
    suspend fun normalizeRepeatRule(uid: String)

    // ============================================================
    // HELPERS
    // ============================================================

    @Query(
        """
        SELECT isDeleted FROM reminders
        WHERE uid = :uid
          AND id = :id
        LIMIT 1
        """
    )
    suspend fun isDeleted(uid: String, id: String): Boolean?
}
