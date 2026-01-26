package com.example.eventreminder.maintenance.gc

/**
 * Immutable report for a manual tombstone garbage collection run.
 *
 * Safe for logging and UI display.
 */
data class TombstoneGcReport(
    val cutoffEpochMillis: Long,
    val retentionDays: Int,

    val localCandidates: Int,
    val remoteCandidates: Int,

    val deletedLocal: Int,
    val deletedRemote: Int,

    val skipped: Int,
    val failedRemoteDeletes: Int,

    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long
)
