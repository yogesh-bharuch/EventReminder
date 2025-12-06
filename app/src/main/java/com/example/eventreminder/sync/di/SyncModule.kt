package com.example.eventreminder.sync.di

// =============================================================
// Imports
// =============================================================
import com.example.eventreminder.data.local.AppDatabase
import com.example.eventreminder.sync.config.ReminderSyncConfigFactory
import com.example.eventreminder.sync.core.*
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides SyncEngine and SyncConfig for the app.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    // --------------------------
    // Firebase Firestore
    // --------------------------
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    // --------------------------
    // User ID Provider
    // --------------------------
    @Provides
    @Singleton
    fun provideUserIdProvider(): UserIdProvider {
        // TODO: Replace with your real auth system
        return UserIdProvider {
            // Example:
            // FirebaseAuth.getInstance().currentUser?.uid
            "TEST_USER_ID"
        }
    }

    // --------------------------
    // Reminder Sync Config
    // --------------------------
    @Provides
    @Singleton
    fun provideReminderSyncEntityConfig(
        firestore: FirebaseFirestore,
        db: AppDatabase
    ): EntitySyncConfig<*> {
        return ReminderSyncConfigFactory.create(
            firestore = firestore,
            reminderDao = db.reminderDao()
        )
    }

    // --------------------------
    // Global SyncConfig
    // --------------------------
    @Provides
    @Singleton
    fun provideSyncConfig(
        userIdProvider: UserIdProvider,
        reminderConfig: EntitySyncConfig<*>
    ): SyncConfig {
        return SyncConfig(
            userIdProvider = userIdProvider,
            entities = listOf(reminderConfig),
            loggingEnabled = true,
            batchSize = 100
        )
    }

    // --------------------------
    // SyncEngine
    // --------------------------
    @Provides
    @Singleton
    fun provideSyncEngine(
        firestore: FirebaseFirestore,
        syncConfig: SyncConfig,
        db: AppDatabase
    ): SyncEngine {
        return SyncEngine(
            firestore = firestore,
            syncConfig = syncConfig,
            syncMetadataDao = db.syncMetadataDao()
        )
    }
}
