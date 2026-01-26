// =============================================================
// TombstoneRemoteDataSourceFirestore.kt
// =============================================================

package com.example.eventreminder.sync.remote.firestore

// =============================================================
// Imports
// =============================================================
import com.example.eventreminder.sync.remote.ReminderCollectionPathProvider
import com.example.eventreminder.sync.remote.TombstoneRemoteDataSource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject
import com.example.eventreminder.logging.DELETE_TAG

/**
 * Firestore-backed implementation of TombstoneRemoteDataSource.
 *
 * ⚠️ Used ONLY by manual tombstone garbage collection.
 * ⚠️ Never called by SyncEngine.
 *
 * IMPORTANT:
 * - Uses FirebaseAuth directly
 * - MUST match SyncEngine user identity source
 */
class TombstoneRemoteDataSourceFirestore @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseAuth: FirebaseAuth,
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

        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            Timber.tag(DELETE_TAG).w("REMOTE_GC findDeletedBefore() skipped — FirebaseAuth user is null")
            return emptyList()
        }

        val collection = collectionPathProvider.remindersCollection(
            firestore = firestore,
            userId = userId
        )

        // 🔍 CRITICAL: Log exact query context
        Timber.tag(DELETE_TAG).e("REMOTE_GC QUERY path=%s userId=%s cutoff=%d", collection.path, userId, cutoffEpochMillis)

        val snapshot = collection
            .whereEqualTo("uid", userId)
            .whereEqualTo("isDeleted", true)
            .whereLessThanOrEqualTo("updatedAt", cutoffEpochMillis)
            .limit(500)
            .get()
            .await()

        // logging for empty snapshot return
        if (snapshot.isEmpty) {
            Timber.tag(DELETE_TAG).w("REMOTE_GC returned 0 results — " + "verify composite index (uid,isDeleted,updatedAt)")
        }

        // 🔍 Log each candidate tombstone
        snapshot.documents.forEach { doc ->
            Timber.tag(DELETE_TAG).e("REMOTE_GC CANDIDATE id=%s updatedAt=%s isDeleted=%s", doc.id, doc.get("updatedAt"), doc.get("isDeleted"))
        }

        val ids = snapshot.documents.map { it.id }

        Timber.tag(DELETE_TAG).i("REMOTE_GC QUERY DONE found=%d", ids.size)

        return ids
    }

    /**
     * Permanently deletes Firestore tombstone documents.
     */
    override suspend fun deleteByIds(
        ids: List<String>
    ): Int {

        if (ids.isEmpty()) {
            Timber.tag(DELETE_TAG).d("REMOTE_GC deleteByIds() skipped — empty list")
            return 0
        }

        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            Timber.tag(DELETE_TAG).w("REMOTE_GC deleteByIds() skipped — FirebaseAuth user is null")
            return 0
        }

        val collection = collectionPathProvider.remindersCollection(
            firestore = firestore,
            userId = userId
        )

        // 🔥 Log delete intent
        Timber.tag(DELETE_TAG).e("REMOTE_GC DELETE REQUEST path=%s ids=%s", collection.path, ids)

        val batch = firestore.batch()
        ids.forEach { id ->
            batch.delete(collection.document(id))
        }

        batch.commit().await()

        // 🔥 Log delete success
        Timber.tag(DELETE_TAG).e("REMOTE_GC DELETE COMMIT DONE deleted=%d", ids.size)

        return ids.size
    }
}
