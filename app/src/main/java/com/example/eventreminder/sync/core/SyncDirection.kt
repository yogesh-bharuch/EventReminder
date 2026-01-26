package com.example.eventreminder.sync.core

/**
 * Defines the direction of synchronization.
 *
 * LOCAL_TO_REMOTE  → Push local Room changes to Firestore only.
 * REMOTE_TO_LOCAL  → Pull remote Firestore changes to Room only.
 * BIDIRECTIONAL    → Perform both sync flows.
 */
enum class SyncDirection {
    LOCAL_TO_REMOTE,
    REMOTE_TO_LOCAL,
    BIDIRECTIONAL
}
