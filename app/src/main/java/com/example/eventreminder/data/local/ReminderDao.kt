package com.example.eventreminder.data.local

import androidx.room.*
import com.example.eventreminder.data.model.EventReminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    // ============================================================
    // INSERT / UPDATE (UUID-based)
    // EventReminder.id is a String -> UUID
    // ============================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: EventReminder)

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
    // DELETE (Soft delete)
    // ============================================================

    @Query("UPDATE reminders SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun markDeleted(id: String, timestamp: Long = System.currentTimeMillis())

    // ============================================================
    // TIMESTAMP HELPERS
    // ============================================================

    @Query("UPDATE reminders SET updatedAt = :timestamp WHERE id = :id")
    suspend fun updateUpdatedAt(id: String, timestamp: Long)

    @Query("SELECT updatedAt FROM reminders WHERE id = :id")
    suspend fun getUpdatedAt(id: String): Long?
}
