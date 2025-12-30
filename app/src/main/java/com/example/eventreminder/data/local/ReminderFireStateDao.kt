package com.example.eventreminder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.eventreminder.data.model.ReminderFireStateEntity

@Dao
interface ReminderFireStateDao {

    // ============================================================
    // Upsert (insert or replace) the per-offset fire state
    // ============================================================
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ReminderFireStateEntity)

    // ============================================================
    // Read helpers
    // ============================================================
    @Query(
        "SELECT lastFiredAt FROM reminder_fire_state " +
                "WHERE reminderId = :id AND offsetMillis = :offsetMillis LIMIT 1"
    )
    suspend fun getLastFiredAt(
        id: String,
        offsetMillis: Long
    ): Long?

    @Query(
        "SELECT * FROM reminder_fire_state WHERE reminderId = :id"
    )
    suspend fun getAllForReminder(
        id: String
    ): List<ReminderFireStateEntity>

    // ============================================================
    // Dismiss bookkeeping (NEW — Step 2A)
    // ============================================================
    @Query(
        "UPDATE reminder_fire_state " +
                "SET dismissedAt = :dismissedAt " +
                "WHERE reminderId = :id AND offsetMillis = :offsetMillis"
    )
    suspend fun updateDismissedAt(
        id: String,
        offsetMillis: Long,
        dismissedAt: Long
    )

    // ============================================================
    // Delete helpers
    // ============================================================
    @Query("DELETE FROM reminder_fire_state WHERE reminderId = :id")
    suspend fun deleteForReminder(id: String)
}
