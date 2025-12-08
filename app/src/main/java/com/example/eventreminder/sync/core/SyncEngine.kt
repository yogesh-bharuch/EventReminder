package com.example.eventreminder.sync.core

// =============================================================
// Imports
// =============================================================
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

            if (maxUpdatedAt == null || updatedAt > maxUpdatedAt!!) {
                maxUpdatedAt = updatedAt
            }

            if (config.isDeleted(local)) {
                batch.set(
                    docRef,
                    mapOf(
                        "uid" to userId,
                        "id" to docId,
                        "isDeleted" to true,
                        "updatedAt" to updatedAt      // LONG epoch millis
                    )
                )
            } else {
                batch.set(docRef, config.toRemote(local, userId)) // also LONG epoch millis
            }
        }

        batch.commit().await()

        // 🔥 Write only if changed
        if (maxUpdatedAt != meta?.lastLocalSyncAt) {
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    key = config.key,
                    lastLocalSyncAt = maxUpdatedAt,
                    lastRemoteSyncAt = meta?.lastRemoteSyncAt
                )
            )
        }

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

        // ---------------------------------------------------------
        // Query ONLY by uid; we do updatedAt filtering + conflicts
        // entirely on the client using plain Long comparison.
        // ---------------------------------------------------------
        var query: Query = config.getCollectionRef()
            .whereEqualTo("uid", userId).limit(500)

        Timber.tag("REMOTE_DEBUG").e(
            "Remote→Local key=%s lastRemoteSyncAt=%s",
            config.key,
            lastRemoteSyncAt ?: "NULL"
        )

        // Fetch documents
        val snapshot = query.get().await()

        Timber.tag("REMOTE_DEBUG").e("Remote query returned %d docs", snapshot.size())

        if (snapshot.isEmpty) {
            Timber.tag("REMOTE_DEBUG").e("No remote docs → EXIT")
            return
        }

        val toUpsert = mutableListOf<Local>()
        val toDeleteIds = mutableListOf<String>()
        var maxRemoteUpdatedAt = lastRemoteSyncAt

        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val docId = doc.id

            val remoteUpdatedAt = extractUpdatedAtMillis(data["updatedAt"])
            val isRemoteDeleted = data["isDeleted"] as? Boolean ?: false
            val localUpdatedAt = config.getLocalUpdatedAt(docId)

            Timber.tag("REMOTE_DEBUG").e(
                "DOC id=%s remoteUpdatedAt=%s localUpdatedAt=%s",
                docId, remoteUpdatedAt, localUpdatedAt
            )

            // ------------------------------
            // Conflict resolution
            // ------------------------------
            val shouldApply = when (config.conflictStrategy) {
                ConflictStrategy.REMOTE_WINS ->
                    // Always trust remote if it's newer than our last remote checkpoint
                    lastRemoteSyncAt == null ||
                            (remoteUpdatedAt != null && remoteUpdatedAt > lastRemoteSyncAt)

                ConflictStrategy.LOCAL_WINS ->
                    // Only apply if we have no local row AND remote is newer than checkpoint
                    localUpdatedAt == null &&
                            (lastRemoteSyncAt == null ||
                                    (remoteUpdatedAt != null && remoteUpdatedAt > lastRemoteSyncAt))

                ConflictStrategy.LATEST_UPDATED_WINS ->
                    // Remote applies if it is newer than what we've ever pulled from cloud
                    lastRemoteSyncAt == null ||
                            (remoteUpdatedAt != null && remoteUpdatedAt > lastRemoteSyncAt)
            }

            Timber.tag("REMOTE_DEBUG").e("shouldApply=%s for id=%s", shouldApply, docId)
            if (!shouldApply) continue

            // Track max remote updatedAt we've seen (for next checkpoint)
            if (remoteUpdatedAt != null &&
                (maxRemoteUpdatedAt == null || remoteUpdatedAt > maxRemoteUpdatedAt!!)
            ) {
                maxRemoteUpdatedAt = remoteUpdatedAt
            }

            // Apply tombstone or upsert
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

        // 🔥 Write only if changed
        if (maxRemoteUpdatedAt != lastRemoteSyncAt) {
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    key = config.key,
                    lastLocalSyncAt = lastLocalSyncAt,
                    lastRemoteSyncAt = maxRemoteUpdatedAt
                )
            )
        }


        Timber.tag("REMOTE_DEBUG")
            .e("SYNC COMPLETE — new lastRemoteSyncAt=%s", maxRemoteUpdatedAt)
    }

    // =============================================================
    // Helper: Normalize updatedAt → millis
    // =============================================================
    private fun extractUpdatedAtMillis(raw: Any?): Long? {
        return when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
    }
}


/*
✅ 5. SyncEngine.kt
💥 THE HEART OF THE ENTIRE SYNC SYSTEM
Responsible for:

A. Local → Remote (Push)
Finds local records with updatedAt > lastLocalSyncAt
Sends them to Firestore
Writes tombstones if deleted
Updates lastLocalSyncAt

B. Remote → Local (Pull)
Fetches Firestore docs for the user
Filters by updatedAt > lastRemoteSyncAt
Applies conflict strategy
Inserts/updates/deletes in Room
Updates lastRemoteSyncAt
👉 This is the sync brain that does the actual work.
* */