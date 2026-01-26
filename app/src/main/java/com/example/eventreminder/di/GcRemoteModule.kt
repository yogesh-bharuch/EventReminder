// =============================================================
// GcRemoteModule.kt
// =============================================================

package com.example.eventreminder.di

// =============================================================
// Imports
// =============================================================
import com.example.eventreminder.sync.remote.TombstoneRemoteDataSource
import com.example.eventreminder.sync.remote.ReminderCollectionPathProvider
import com.example.eventreminder.sync.remote.firestore.TombstoneRemoteDataSourceFirestore
import com.example.eventreminder.sync.remote.firestore.DefaultReminderCollectionPathProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for tombstone garbage collection (remote).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GcRemoteModule {

    // ------------------------------------------------------------
    // Tombstone GC – Firestore
    // ------------------------------------------------------------
    @Binds
    @Singleton
    abstract fun bindTombstoneRemoteDataSource(
        impl: TombstoneRemoteDataSourceFirestore
    ): TombstoneRemoteDataSource

    // ------------------------------------------------------------
    // Firestore collection path provider
    // ------------------------------------------------------------
    @Binds
    @Singleton
    abstract fun bindReminderCollectionPathProvider(
        impl: DefaultReminderCollectionPathProvider
    ): ReminderCollectionPathProvider
}
