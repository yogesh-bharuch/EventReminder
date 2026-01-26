// =============================================================
// SyncResult.kt
// =============================================================
package com.example.eventreminder.sync.core

/**
 * SyncResult
 *
 * Canonical result object produced by SyncEngine.
 * Field names are explicit and UI-friendly.
 */
data class SyncResult(

    // -------------------------------
    // Local → Remote
    // -------------------------------
    var localToRemoteCreated: Int = 0,
    var localToRemoteUpdated: Int = 0,
    var localToRemoteDeleted: Int = 0,
    var localToRemoteSkipped: Int = 0,

    // -------------------------------
    // Remote → Local
    // -------------------------------
    var remoteToLocalCreated: Int = 0,
    var remoteToLocalUpdated: Int = 0,
    var remoteToLocalDeleted: Int = 0,
    var remoteToLocalSkipped: Int = 0,

    // -------------------------------
    // Meta (NEW — non-breaking)
    // -------------------------------
    var blockedReason: SyncBlockedReason? = null
) {

    /**
     * True if sync caused no material changes
     * (creates / updates / deletes in either direction).
     */
    fun isEmpty(): Boolean =
        localToRemoteCreated +
                localToRemoteUpdated +
                localToRemoteDeleted +
                remoteToLocalCreated +
                remoteToLocalUpdated +
                remoteToLocalDeleted == 0
}

/**
 * Explicit sync block reasons.
 * Used ONLY for UI feedback.
 */
enum class SyncBlockedReason {
    USER_NOT_LOGGED_IN,
    EMAIL_NOT_VERIFIED,
    NO_INTERNET
}