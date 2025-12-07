package com.example.eventreminder.sync.core

/**
 * Defines how to resolve conflicts when both local and remote
 * copies have changed since the last sync checkpoint.
 */
enum class ConflictStrategy {
    LOCAL_WINS,
    REMOTE_WINS,
    LATEST_UPDATED_WINS
}
