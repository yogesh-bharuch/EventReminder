package com.example.eventreminder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.eventreminder.data.Converters
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.sync.core.SyncMetadataDao
import com.example.eventreminder.sync.core.SyncMetadataEntity

@Database(
    entities = [
        EventReminder::class,
        SyncMetadataEntity::class  // REQUIRED for sync engine
    ],
    version = 4,                 // bump version
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun syncMetadataDao(): SyncMetadataDao
}
