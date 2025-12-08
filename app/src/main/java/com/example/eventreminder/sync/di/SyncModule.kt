package com.example.eventreminder.sync.di

// =============================================================
// Imports
// =============================================================
import com.example.eventreminder.data.local.AppDatabase
import com.example.eventreminder.sync.config.ReminderSyncConfig
import com.example.eventreminder.sync.core.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
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
        return UserIdProvider {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            Timber.tag("SYNC_UID").i("Providing UID = %s", uid)
            uid
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
        return ReminderSyncConfig.create(
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

/*
* ✅ 8. SyncModule.kt (Hilt DI Module)
Provides:
Firestore instance
UserIdProvider
ReminderSyncConfig
Global SyncConfig with entity list
SyncEngine instance
👉 This wires up all sync components using Hilt Dependency Injection.
* */