package com.example.eventreminder.sync.di

// =============================================================
// Imports
// =============================================================
import com.example.eventreminder.data.local.AppDatabase
import com.example.eventreminder.sync.core.SyncMetadataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides SyncMetadataDao only.
 * AppDatabase is already provided in DatabaseModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncMetadataModule {

    @Provides
    @Singleton
    fun provideSyncMetadataDao(db: AppDatabase): SyncMetadataDao {
        return db.syncMetadataDao()
    }
}
