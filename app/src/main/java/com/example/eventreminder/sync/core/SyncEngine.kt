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
    /**
     * Local → Remote Sync
     *
     * STRATEGY:
     * - Push local changes to Firestore
     * - Push tombstones for deleted records
     * - NEVER overwrite an existing remote tombstone
     * - NEVER resurrect a deleted record
     *
     * IMPORTANT RULE:
     *   If Firestore already has isDeleted=true for a document,
     *   that document is FINAL and must NEVER be overwritten,
     *   even if local.updatedAt is newer.
     */
    private suspend fun <Local : Any> syncLocalToRemote(
        userId: String,
        config: EntitySyncConfig<Local>
    ) {
        val meta = syncMetadataDao.get(config.key)
        val lastLocalSyncAt = meta?.lastLocalSyncAt

        // Fetch local rows changed since last sync (includes deleted rows)
        val changedLocals = config.daoAdapter.getLocalsChangedAfter(lastLocalSyncAt)
        if (changedLocals.isEmpty()) return

        val batch = firestore.batch()
        val collection = config.getCollectionRef()
        var maxUpdatedAt = lastLocalSyncAt

        for (local in changedLocals) {

            val localUpdatedAt = config.getUpdatedAt(local)
            val docId = config.getLocalId(local)
            val docRef = collection.document(docId)

            // Track checkpoint
            if (maxUpdatedAt == null || localUpdatedAt > maxUpdatedAt!!) {
                maxUpdatedAt = localUpdatedAt
            }

            // ------------------------------------------------------------
            // 🔥 CRITICAL GUARD: READ REMOTE STATE
            // ------------------------------------------------------------
            val remoteSnapshot = docRef.get().await()
            val remoteData = remoteSnapshot.data
            val remoteDeleted = remoteData?.get("isDeleted") as? Boolean ?: false
            val remoteUpdatedAt = (remoteData?.get("updatedAt") as? Number)?.toLong()

            /**
             * RULE #1 — Remote tombstone is FINAL
             *
             * If Firestore already has isDeleted=true,
             * DO NOT upload anything — EVER.
             *
             * This guards against:
             * - Device-2 editing after device-1 deleted
             * - Resurrection bugs
             */
            if (remoteDeleted) {
                Timber.tag(TAG).w(
                    "Skip upload: REMOTE TOMBSTONE EXISTS id=%s",
                    docId
                )
                continue
            }

            /**
             * RULE #2 — Local tombstone upload
             *
             * Upload a tombstone ONLY IF:
             * - This deletion has not already been synced
             */
            if (config.isDeleted(local)) {

                // Tombstone already pushed earlier → skip re-upload
                if (lastLocalSyncAt != null && localUpdatedAt <= lastLocalSyncAt) {
                    Timber.tag(TAG).d(
                        "Skip tombstone re-upload id=%s (already synced)",
                        docId
                    )
                    continue
                }

                // Upload new tombstone
                batch.set(
                    docRef,
                    mapOf(
                        "uid" to userId,
                        "id" to docId,
                        "isDeleted" to true,
                        "updatedAt" to localUpdatedAt
                    )
                )
                continue
            }

            /**
             * RULE #3 — Remote newer than local
             *
             * If Firestore has a newer version,
             * local is stale → skip upload
             */
            if (remoteUpdatedAt != null && remoteUpdatedAt > localUpdatedAt) {
                Timber.tag(TAG).d(
                    "Skip upload: remote newer id=%s (remote=%s > local=%s)",
                    docId, remoteUpdatedAt, localUpdatedAt
                )
                continue
            }

            /**
             * RULE #4 — Normal update
             *
             * Local update is newer → push to Firestore
             */
            batch.set(docRef, config.toRemote(local, userId))
        }

        batch.commit().await()

        // Advance checkpoint ONLY if changed
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
    /**
     * Remote → Local Sync
     *
     * STRATEGY:
     * - Pull Firestore documents for the user
     * - Apply REMOTE TOMBSTONES FIRST
     * - Tombstones ALWAYS delete locally
     * - Conflict strategies NEVER override deletes
     *
     * GOLDEN RULE:
     *   If Firestore says isDeleted=true,
     *   local record MUST be deleted — always.
     */
    private suspend fun <Local : Any> syncRemoteToLocal(
        userId: String,
        config: EntitySyncConfig<Local>
    ) {
        val meta = syncMetadataDao.get(config.key)
        val lastRemoteSyncAt = meta?.lastRemoteSyncAt
        val lastLocalSyncAt = meta?.lastLocalSyncAt

        val snapshot = config.getCollectionRef()
            .whereEqualTo("uid", userId)
            .limit(500)
            .get()
            .await()

        if (snapshot.isEmpty) return

        val toUpsert = mutableListOf<Local>()
        val toDeleteIds = mutableListOf<String>()
        var maxRemoteUpdatedAt = lastRemoteSyncAt

        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val docId = doc.id

            val remoteUpdatedAt = extractUpdatedAtMillis(data["updatedAt"])
            val isRemoteDeleted = data["isDeleted"] as? Boolean ?: false
            val localUpdatedAt = config.getLocalUpdatedAt(docId)

            /**
             * 🔥 RULE #1 — REMOTE TOMBSTONE
             *
             * ALWAYS apply delete locally.
             * Conflict strategy is IGNORED.
             */
            if (isRemoteDeleted) {

                Timber.tag("REMOTE_DEBUG")
                    .e("REMOTE TOMBSTONE → delete local id=%s", docId)

                toDeleteIds.add(docId)

                if (remoteUpdatedAt != null &&
                    (maxRemoteUpdatedAt == null || remoteUpdatedAt > maxRemoteUpdatedAt!!)
                ) {
                    maxRemoteUpdatedAt = remoteUpdatedAt
                }
                continue
            }

            /**
             * RULE #2 — Conflict resolution for NON-deleted records
             */
            val shouldApply = when (config.conflictStrategy) {
                ConflictStrategy.REMOTE_WINS ->
                    lastRemoteSyncAt == null ||
                            (remoteUpdatedAt != null && remoteUpdatedAt > lastRemoteSyncAt)

                ConflictStrategy.LOCAL_WINS ->
                    localUpdatedAt == null &&
                            (lastRemoteSyncAt == null ||
                                    (remoteUpdatedAt != null && remoteUpdatedAt > lastRemoteSyncAt))

                ConflictStrategy.LATEST_UPDATED_WINS ->
                    lastRemoteSyncAt == null ||
                            (remoteUpdatedAt != null && remoteUpdatedAt > lastRemoteSyncAt)
            }

            if (!shouldApply) continue

            if (remoteUpdatedAt != null &&
                (maxRemoteUpdatedAt == null || remoteUpdatedAt > maxRemoteUpdatedAt!!)
            ) {
                maxRemoteUpdatedAt = remoteUpdatedAt
            }

            // Normal upsert
            toUpsert.add(config.fromRemote(docId, data))
        }

        if (toUpsert.isNotEmpty()) {
            config.daoAdapter.upsertAll(toUpsert)
        }

        if (toDeleteIds.isNotEmpty()) {
            config.daoAdapter.markDeletedByIds(toDeleteIds)
        }

        if (maxRemoteUpdatedAt != lastRemoteSyncAt) {
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    key = config.key,
                    lastLocalSyncAt = lastLocalSyncAt,
                    lastRemoteSyncAt = maxRemoteUpdatedAt
                )
            )
        }
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