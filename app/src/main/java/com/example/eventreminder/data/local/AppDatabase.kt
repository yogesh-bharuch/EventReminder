package com.example.eventreminder.data.local


import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.eventreminder.data.Converters
import com.example.eventreminder.data.model.EventReminder

@Database(
    entities = [EventReminder::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
}