package com.example.eventreminder.sync.core

/**
 * Root configuration passed to SyncEngine.
 */
data class SyncConfig(
    val userIdProvider: UserIdProvider,
    val entities: List<EntitySyncConfig<*>>,
    val loggingEnabled: Boolean = true,
    val batchSize: Int = 100
)
