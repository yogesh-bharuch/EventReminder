package com.example.eventreminder.sync.core

// =============================================================
// Imports
// =============================================================
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * SyncEngine
 *
 * Bi-directional synchronization engine for Firestore <-> Room.
 * Generic, works for any entity type through EntitySyncConfig.
 *
 * Soft delete model:
 * - Local delete -> Firestore tombstone { isDeleted = true }
 * - Firestore tombstone -> local markDeletedByIds()
 */
class SyncEngine(
    private val firestore: FirebaseFirestore,
    private val syncConfig: SyncConfig,
    private val syncMetadataDao: SyncMetadataDao
) {

    companion object { private const val TAG = "SyncEngine" }

    // =============================================================
    // Sync Entry Point
    // =============================================================
    suspend fun syncAll() {
        if (syncConfig.loggingEnabled) {
            Timber.tag(TAG).i("Starting sync for %d entities", syncConfig.entities.size)
        }

        val userId = syncConfig.userIdProvider.getUserId()
        if (userId == null) {
            Timber.tag(TAG).w("Skipping sync: userId is null.")
            return
        }

        Timber.tag(TAG).i("Sync using userId=%s", userId)

        for (raw in syncConfig.entities) {
            @Suppress("UNCHECKED_CAST")
            val config = raw as EntitySyncConfig<Any>

            try {
                when (config.direction) {
                    SyncDirection.LOCAL_TO_REMOTE -> syncLocalToRemote(userId, config)
                    SyncDirection.REMOTE_TO_LOCAL -> syncRemoteToLocal(userId, config)
                    SyncDirection.BIDIRECTIONAL -> {
                        syncLocalToRemote(userId, config)
                        syncRemoteToLocal(userId, config)
                    }
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Error syncing key=%s", config.key)
            }
        }

        if (syncConfig.loggingEnabled) {
            Timber.tag(TAG).i("Sync complete.")
        }
    }

    // =============================================================
    // Local → Remote
    // =============================================================
    private suspend fun <Local : Any> syncLocalToRemote(
        userId: String,
        config: EntitySyncConfig<Local>
    ) {
        val meta = syncMetadataDao.get(config.key)
        val lastLocalSyncAt = meta?.lastLocalSyncAt

        val changedLocals = config.daoAdapter.getLocalsChangedAfter(lastLocalSyncAt)

        if (syncConfig.loggingEnabled) {
            Timber.tag(TAG).i(
                "Local→Remote [%s] lastLocalSyncAt=%s changed=%d",
                config.key,
                lastLocalSyncAt ?: -1,
                changedLocals.size
            )
        }

        if (changedLocals.isEmpty()) return

        val batch = firestore.batch()
        val collection = config.getCollectionRef()

        var maxUpdatedAt = lastLocalSyncAt

        for (local in changedLocals) {
            val updatedAt = config.getUpdatedAt(local)
            val docId = config.getLocalId(local)
            val docRef = collection.document(docId)

            if (maxUpdatedAt == null || updatedAt > maxUpdatedAt!!) maxUpdatedAt = updatedAt

            if (config.isDeleted(local)) {

                // Ensure Firestore always stores updatedAt as Timestamp, even for tombstones
                val updatedMillis = updatedAt
                val ts = Timestamp(
                    updatedMillis / 1000,
                    ((updatedMillis % 1000) * 1_000_000).toInt()
                )

                batch.set(
                    docRef,
                    mapOf(
                        "uid" to userId,
                        "id" to docId,
                        "isDeleted" to true,
                        "updatedAt" to ts
                    )
                )
            } else {
                batch.set(docRef, config.toRemote(local, userId))
            }
        }

        batch.commit().await()

        syncMetadataDao.upsert(
            SyncMetadataEntity(
                key = config.key,
                lastLocalSyncAt = maxUpdatedAt,
                lastRemoteSyncAt = meta?.lastRemoteSyncAt
            )
        )
    }

    // =============================================================
    // Remote → Local
    // =============================================================
    private suspend fun <Local : Any> syncRemoteToLocal(
        userId: String,
        config: EntitySyncConfig<Local>
    ) {
        Timber.tag("REMOTE_DEBUG").e("ENTERED syncRemoteToLocal()")

        val meta = syncMetadataDao.get(config.key)
        val lastRemoteSyncAt = meta?.lastRemoteSyncAt
        val lastLocalSyncAt = meta?.lastLocalSyncAt

        // -------------------------------
        // Use Timestamp filter (CORRECT)
        // -------------------------------
        var query: Query = config.getCollectionRef()
            .whereEqualTo("uid", userId)

        if (lastRemoteSyncAt != null) {

            val ts = Timestamp(
                lastRemoteSyncAt / 1000,
                ((lastRemoteSyncAt % 1000) * 1_000_000).toInt()
            )

            Timber.tag("REMOTE_DEBUG").e("Filtering where updatedAt > %s", ts)
            query = query.whereGreaterThan("updatedAt", ts)

        } else {
            Timber.tag("REMOTE_DEBUG").e("No lastRemoteSyncAt → FULL SCAN")
        }

        // Fetch documents
        val snapshot = query.get().await()

        Timber.tag("REMOTE_DEBUG").e("Remote query returned %d docs", snapshot.size())

        for (doc in snapshot.documents) {
            val raw = doc.data?.get("updatedAt")
            Timber.tag("REMOTE_DEBUG").e(
                "REMOTE DOC id=%s updatedAt RAW=%s TYPE=%s",
                doc.id,
                raw,
                raw?.javaClass?.simpleName
            )
        }

        if (snapshot.isEmpty) {
            Timber.tag("REMOTE_DEBUG").e("No remote updates → EXIT")
            return
        }

        val toUpsert = mutableListOf<Local>()
        val toDeleteIds = mutableListOf<String>()
        var maxRemoteUpdatedAt = lastRemoteSyncAt

        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val docId = doc.id

            // ------------------------------------
            // USE HELPER to extract millis
            // ------------------------------------
            val remoteUpdatedAt: Long? = extractUpdatedAtMillis(data["updatedAt"])
            val isRemoteDeleted = data["isDeleted"] as? Boolean ?: false
            val localUpdatedAt = config.getLocalUpdatedAt(docId)

            Timber.tag("REMOTE_DEBUG").e(
                "PROCESS id=%s remoteUpdatedAt=%s localUpdatedAt=%s",
                docId, remoteUpdatedAt, localUpdatedAt
            )

            // ------------------------------------
            // Correct conflict logic: LATEST_UPDATED_WINS
            // ------------------------------------
            val shouldApply = when (config.conflictStrategy) {
                ConflictStrategy.REMOTE_WINS ->
                    true

                ConflictStrategy.LOCAL_WINS ->
                    localUpdatedAt == null // ONLY override if local doesn't exist

                ConflictStrategy.LATEST_UPDATED_WINS ->
                    localUpdatedAt == null ||
                            (remoteUpdatedAt != null && remoteUpdatedAt > localUpdatedAt)
            }

            Timber.tag("REMOTE_DEBUG").e("shouldApply=%s", shouldApply)
            if (!shouldApply) continue

            if (remoteUpdatedAt != null &&
                (maxRemoteUpdatedAt == null || remoteUpdatedAt > maxRemoteUpdatedAt!!)
            ) {
                maxRemoteUpdatedAt = remoteUpdatedAt
            }

            if (isRemoteDeleted) {
                toDeleteIds.add(docId)
            } else {
                toUpsert.add(config.fromRemote(docId, data))
            }
        }

        if (toUpsert.isNotEmpty()) {
            Timber.tag("REMOTE_DEBUG").e("Upserting %d records → ROOM", toUpsert.size)
            config.daoAdapter.upsertAll(toUpsert)
        }

        if (toDeleteIds.isNotEmpty()) {
            Timber.tag("REMOTE_DEBUG").e("Deleting %d records → ROOM", toDeleteIds.size)
            config.daoAdapter.markDeletedByIds(toDeleteIds)
        }

        syncMetadataDao.upsert(
            SyncMetadataEntity(
                key = config.key,
                lastLocalSyncAt = lastLocalSyncAt,
                lastRemoteSyncAt = maxRemoteUpdatedAt
            )
        )

        Timber.tag("REMOTE_DEBUG")
            .e("SYNC COMPLETE — new lastRemoteSyncAt=%s", maxRemoteUpdatedAt)
    }

    // =============================================================
    // Helper: Normalize remote updatedAt → millis
    // =============================================================
    private fun extractUpdatedAtMillis(raw: Any?): Long? {
        return when (raw) {
            is Timestamp -> raw.toDate().time
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
    }
}
