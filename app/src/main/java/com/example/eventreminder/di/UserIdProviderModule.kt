package com.example.eventreminder.di

import com.example.eventreminder.sync.core.SessionUserIdProvider
import com.example.eventreminder.sync.core.UserIdProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UserIdProviderModule {

    @Binds
    @Singleton
    abstract fun bindUserIdProvider(
        impl: SessionUserIdProvider
    ): UserIdProvider
}
