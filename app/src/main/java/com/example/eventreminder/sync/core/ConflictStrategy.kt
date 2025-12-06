package com.example.eventreminder.sync.core

/**
 * Defines how to resolve conflicts when both local and remote
 * copies have changed since the last sync checkpoint.
 */
enum class ConflictStrategy {

    /** Always choose local copy, overwrite Firestore. */
    LOCAL_WINS,

    /** Always choose remote copy, overwrite Room. */
    REMOTE_WINS,

    /** Choose whichever has the latest "updatedAt" timestamp. */
    LATEST_UPDATED_WINS
}
