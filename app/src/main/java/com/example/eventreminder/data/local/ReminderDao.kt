package com.example.eventreminder.data.local

import androidx.room.*
import com.example.eventreminder.data.model.EventReminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: EventReminder): Long

    @Update
    suspend fun update(reminder: EventReminder)

    @Query("SELECT * FROM reminders WHERE isDeleted = 0 ORDER BY id DESC")
    fun getAll(): Flow<List<EventReminder>>

    // Used by BootReceiver for rescheduling
    @Query("SELECT * FROM reminders WHERE isDeleted = 0")
    suspend fun getAllOnce(): List<EventReminder>

    // ReminderSyncDaoAdapter to sync deleted records too
    @Query("SELECT * FROM reminders")
    suspend fun getAllIncludingDeletedOnce(): List<EventReminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): EventReminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<EventReminder>)

    @Query("UPDATE reminders SET isDeleted = 1 WHERE id = :id")
    suspend fun markDeleted(id: Long)

    @Query("UPDATE reminders SET updatedAt = :timestamp WHERE id = :id")
    suspend fun updateUpdatedAt(id: Long, timestamp: Long)

    @Query("SELECT updatedAt FROM reminders WHERE id = :id")
    suspend fun getUpdatedAt(id: Long): Long?



}
