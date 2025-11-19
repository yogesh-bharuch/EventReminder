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

    @Delete
    suspend fun delete(reminder: EventReminder)

    @Query("SELECT * FROM reminders ORDER BY id DESC")
    fun getAll(): Flow<List<EventReminder>>

    // Used by BootReceiver for rescheduling
    @Query("SELECT * FROM reminders")
    suspend fun getAllOnce(): List<EventReminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): EventReminder?
}
