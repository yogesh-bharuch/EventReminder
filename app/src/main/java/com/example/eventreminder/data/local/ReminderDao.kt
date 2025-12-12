package com.example.eventreminder.data.local

import androidx.room.*
import com.example.eventreminder.data.model.EventReminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    // ============================================================
    // INSERT / UPDATE (UUID-based)
    // ============================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: EventReminder): Long

    @Update
    suspend fun update(reminder: EventReminder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<EventReminder>)

    // ============================================================
    // READ
    // ============================================================

    @Query("SELECT * FROM reminders WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAll(): Flow<List<EventReminder>>

    @Query("SELECT * FROM reminders WHERE isDeleted = 0")
    suspend fun getAllOnce(): List<EventReminder>

    @Query("SELECT * FROM reminders")
    suspend fun getAllIncludingDeletedOnce(): List<EventReminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): EventReminder?

    // ============================================================
    // DELETE
    // ============================================================

    /**
     * Local soft-delete: sets isDeleted=1 and updates updatedAt so local change will be pushed.
     * Used when the user deletes within this device.
     */
    @Query("UPDATE reminders SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun markDeleted(id: String, timestamp: Long = System.currentTimeMillis())

    /**
     * Remote tombstone application:
     * Marks isDeleted=1 but DOES NOT modify updatedAt.
     * Use this when applying a delete coming FROM the cloud.
     *
     * This prevents creating a new local updatedAt that would re-trigger a Local→Remote push.
     */
    @Query("UPDATE reminders SET isDeleted = 1 WHERE id = :id")
    suspend fun markDeletedRemote(id: String)

    // ============================================================
    // TIMESTAMP HELPERS
    // ============================================================

    @Query("UPDATE reminders SET updatedAt = :timestamp WHERE id = :id")
    suspend fun updateUpdatedAt(id: String, timestamp: Long)

    @Query("SELECT updatedAt FROM reminders WHERE id = :id")
    suspend fun getUpdatedAt(id: String): Long?
}
