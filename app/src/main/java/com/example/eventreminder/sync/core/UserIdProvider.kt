package com.example.eventreminder.sync.core

/**
 * Provides the currently authenticated user's UID.
 *
 * The App implements this (e.g., using FirebaseAuth) and injects it
 * into the SyncConfig. This keeps sync module reusable across apps
 * without depending on Firebase's Auth API internally.
 */
fun interface UserIdProvider {
    suspend fun getUserId(): String?
}
