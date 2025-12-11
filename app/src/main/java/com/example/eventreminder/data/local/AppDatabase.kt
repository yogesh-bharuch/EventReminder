package com.example.eventreminder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.eventreminder.data.Converters
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.model.ReminderFireStateEntity
import com.example.eventreminder.sync.core.SyncMetadataDao
import com.example.eventreminder.sync.core.SyncMetadataEntity

@Database(
    entities = [
        EventReminder::class,
        SyncMetadataEntity::class,      // REQUIRED for sync engine
        ReminderFireStateEntity::class  // Per-offset lastFiredAt state
    ],
    version = 2,                 // bumped for new entity (safe if you uninstall dev build)
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun reminderFireStateDao(): ReminderFireStateDao
    abstract fun syncMetadataDao(): SyncMetadataDao
}
