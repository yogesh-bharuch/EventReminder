
package com.example.eventreminder.sync.remote

/**
 * Remote access abstraction for tombstone garbage collection.
 *
 * Firestore-backed implementation will be provided separately.
 */
interface TombstoneRemoteDataSource {

    /**
     * Returns reminder UUIDs that are marked deleted and older than cutoff.
     */
    suspend fun findDeletedBefore(
        cutoffEpochMillis: Long
    ): List<String>

    /**
     * Permanently deletes remote tombstone documents by UUID.
     *
     * @return number of successfully deleted documents
     */
    suspend fun deleteByIds(
        ids: List<String>
    ): Int
}
