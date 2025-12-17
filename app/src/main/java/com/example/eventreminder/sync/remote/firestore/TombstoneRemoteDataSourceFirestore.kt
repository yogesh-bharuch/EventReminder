// =============================================================
// TombstoneRemoteDataSourceFirestore.kt
// =============================================================

package com.example.eventreminder.sync.remote.firestore

// =============================================================
// Imports
// =============================================================
import com.example.eventreminder.sync.core.UserIdProvider
import com.example.eventreminder.sync.remote.ReminderCollectionPathProvider
import com.example.eventreminder.sync.remote.TombstoneRemoteDataSource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

/**
 * Firestore-backed implementation of TombstoneRemoteDataSource.
 *
 * ⚠️ Used ONLY by manual tombstone garbage collection.
 * ⚠️ Never called by SyncEngine.
 */
class TombstoneRemoteDataSourceFirestore @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userIdProvider: UserIdProvider,
    private val collectionPathProvider: ReminderCollectionPathProvider
) : TombstoneRemoteDataSource {

    private companion object {
        const val TAG = "TombstoneRemoteDS"
    }

    /**
     * Returns reminder IDs that are tombstones older than cutoff.
     */
    override suspend fun findDeletedBefore(
        cutoffEpochMillis: Long
    ): List<String> {

        val userId = userIdProvider.getUserId()
        if (userId == null) {
            Timber.tag(TAG).w("findDeletedBefore() skipped — userId is null")
            return emptyList()
        }

        val collection = collectionPathProvider.remindersCollection(
            firestore = firestore,
            userId = userId
        )

        Timber.tag(TAG).i(
            "findDeletedBefore() START cutoff=%d",
            cutoffEpochMillis
        )

        val snapshot = collection
            .whereEqualTo("uid", userId)
            .whereEqualTo("isDeleted", true)
            .whereLessThan("updatedAt", cutoffEpochMillis)
            .limit(500)
            .get()
            .await()

        val ids = snapshot.documents.map { it.id }

        Timber.tag(TAG).i(
            "findDeletedBefore() DONE found=%d",
            ids.size
        )

        return ids
    }

    /**
     * Permanently deletes Firestore tombstone documents.
     */
    override suspend fun deleteByIds(
        ids: List<String>
    ): Int {

        if (ids.isEmpty()) {
            Timber.tag(TAG).d("deleteByIds() skipped — empty list")
            return 0
        }

        val userId = userIdProvider.getUserId()
        if (userId == null) {
            Timber.tag(TAG).w("deleteByIds() skipped — userId is null")
            return 0
        }

        val collection = collectionPathProvider.remindersCollection(
            firestore = firestore,
            userId = userId
        )

        Timber.tag(TAG).w(
            "deleteByIds() START count=%d",
            ids.size
        )

        val batch = firestore.batch()
        ids.forEach { id ->
            batch.delete(collection.document(id))
        }

        batch.commit().await()

        Timber.tag(TAG).w(
            "deleteByIds() DONE deleted=%d",
            ids.size
        )

        return ids.size
    }
}
