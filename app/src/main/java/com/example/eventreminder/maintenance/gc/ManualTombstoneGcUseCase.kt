package com.example.eventreminder.maintenance.gc

/**
 * Manual tombstone garbage collection use-case.
 */
interface ManualTombstoneGcUseCase {

    /**
     * Runs tombstone garbage collection and returns a report.
     */
    suspend fun run(
        nowEpochMillis: Long,
        retentionDays: Int = 30
    ): TombstoneGcReport
}
