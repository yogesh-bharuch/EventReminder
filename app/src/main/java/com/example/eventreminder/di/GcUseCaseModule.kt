// =============================================================
// GcUseCaseModule.kt
// =============================================================

package com.example.eventreminder.di

// =============================================================
// Imports
// =============================================================
import com.example.eventreminder.maintenance.gc.ManualTombstoneGcUseCase
import com.example.eventreminder.maintenance.gc.ManualTombstoneGcUseCaseImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for reminder lifecycle / GC use-cases.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GcUseCaseModule {

    @Binds
    @Singleton
    abstract fun bindManualTombstoneGcUseCase(
        impl: ManualTombstoneGcUseCaseImpl
    ): ManualTombstoneGcUseCase
}
