package com.example.eventreminder.sync.di

// =============================================================
// Imports
// =============================================================
import android.content.Context
import com.example.eventreminder.data.local.AppDatabase
import com.example.eventreminder.sync.config.ReminderSyncConfig
import com.example.eventreminder.sync.core.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Singleton

/**
 * SyncModule
 *
 * Provides:
 * - FirebaseFirestore
 * - UserIdProvider (FirebaseAuth-backed)
 * - ReminderSyncConfig (UID-aware)
 * - Global SyncConfig
 * - SyncEngine
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
    // Reminder Sync Entity Config
    // --------------------------
    @Provides
    @Singleton
    fun provideReminderSyncEntityConfig(
        firestore: FirebaseFirestore,
        db: AppDatabase,
        userIdProvider: UserIdProvider      // ✅ ADDED
    ): EntitySyncConfig<*> {

        return ReminderSyncConfig.create(
            firestore = firestore,
            reminderDao = db.reminderDao(),
            userIdProvider = userIdProvider   // ✅ FIX
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
        @ApplicationContext context: Context,
        firestore: FirebaseFirestore,
        syncConfig: SyncConfig,
        db: AppDatabase
    ): SyncEngine {

        return SyncEngine(
            context = context,
            firestore = firestore,
            syncConfig = syncConfig,
            syncMetadataDao = db.syncMetadataDao()
        )
    }
}
