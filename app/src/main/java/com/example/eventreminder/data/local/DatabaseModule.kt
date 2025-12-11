package com.example.eventreminder.data.local

// =============================================================
// Imports
// =============================================================
import android.content.Context
import androidx.room.Room
import com.example.eventreminder.sync.core.SyncMetadataDao
import com.example.eventreminder.util.NextOccurrenceCalculator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// =============================================================
// DatabaseModule — Provides Room Database + DAOs + Utilities
// =============================================================
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // ---------------------------------------------------------
    // Provide AppDatabase (Room)
    // ---------------------------------------------------------
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "event_reminder_db"
        )
            // Pre-production only — wipes DB on schema change
            .fallbackToDestructiveMigration()
            .build()
    }

    // ---------------------------------------------------------
    // Provide ReminderDao
    // ---------------------------------------------------------
    @Provides
    @Singleton
    fun provideReminderDao(
        db: AppDatabase
    ): ReminderDao = db.reminderDao()

    // ---------------------------------------------------------
    // Provide ReminderFireStateDao (NEW for Option A Extended)
    // ---------------------------------------------------------
    @Provides
    @Singleton
    fun provideReminderFireStateDao(
        db: AppDatabase
    ): ReminderFireStateDao = db.reminderFireStateDao()

    // ---------------------------------------------------------
    // Provide SyncMetadataDao
    // ---------------------------------------------------------
    @Provides
    @Singleton
    fun provideSyncMetadataDao(
        db: AppDatabase
    ): SyncMetadataDao = db.syncMetadataDao()

    // ---------------------------------------------------------
    // Provide NextOccurrenceCalculator
    // ---------------------------------------------------------
    @Provides
    @Singleton
    fun provideNextOccurrenceCalculator(): NextOccurrenceCalculator =
        NextOccurrenceCalculator
}
