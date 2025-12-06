package com.example.eventreminder.sync.core

// =============================================================
// Imports
// =============================================================
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import timber.log.Timber

// =============================================================
// SyncEngine
// =============================================================

/**
 * Main orchestrator responsible for synchronizing all configured
 * entity types between Room and Firestore.
 *
 * This class is intentionally generic and does NOT know any
 * app-specific entities or DAOs.
 */
class SyncEngine(
    private val firestore: FirebaseFirestore,
    private val syncConfig: SyncConfig,
    private val syncMetadataDao: SyncMetadataDao
) {

    companion object {
        private const val TAG = "SyncEngine"
    }

    /**
     * Entry point to trigger synchronization for all configured entities.
     *
     * Typical usage:
     * - Manually from a "Sync now" button.
     * - From a WorkManager worker on a background schedule.
     */
    suspend fun syncAll() {
        if (syncConfig.loggingEnabled) {
            Timber.tag(TAG).i("Starting sync for %d entities", syncConfig.entities.size)
        }

        val userId = syncConfig.userIdProvider.getUserId()
        if (userId == null) {
            Timber.tag(TAG).w("Skipping sync: userId is null (not authenticated).")
            return
        }

        syncConfig.entities.forEach { rawConfig ->
            @Suppress("UNCHECKED_CAST")
            val config = rawConfig as EntitySyncConfig<Any>

            try {
                if (syncConfig.loggingEnabled) {
                    Timber.tag(TAG).i(
                        "Syncing entity key=%s direction=%s",
                        config.key,
                        config.direction
                    )
                }

                when (config.direction) {
                    SyncDirection.LOCAL_TO_REMOTE -> {
                        syncLocalToRemote(userId, config)
                    }
                    SyncDirection.REMOTE_TO_LOCAL -> {
                        syncRemoteToLocal(userId, config)
                    }
                    SyncDirection.BIDIRECTIONAL -> {
                        // NOTE: Order is important for perceived conflict behavior.
                        syncLocalToRemote(userId, config)
                        syncRemoteToLocal(userId, config)
                    }
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Error syncing entity key=%s", config.key)
            }
        }

        if (syncConfig.loggingEnabled) {
            Timber.tag(TAG).i("Sync completed for all entities.")
        }
    }

    // =============================================================
    // Local → Remote
    // =============================================================

    private suspend fun <Local : Any> syncLocalToRemote(
        userId: String,
        config: EntitySyncConfig<Local>
    ) {
        // 1) Load existing metadata (checkpoint)
        val meta = syncMetadataDao.get(config.key)
        val lastLocalSyncAt = meta?.lastLocalSyncAt

        if (syncConfig.loggingEnabled) {
            Timber.tag(TAG).i(
                "Local→Remote [%s] - lastLocalSyncAt=%s",
                config.key,
                lastLocalSyncAt?.toString() ?: "null"
            )
        }

        // 2) Fetch all local changes after lastLocalSyncAt
        val changedLocals = config.daoAdapter.getLocalsChangedAfter(lastLocalSyncAt)

        if (changedLocals.isEmpty()) {
            if (syncConfig.loggingEnabled) {
                Timber.tag(TAG).i("Local→Remote [%s] - no local changes to sync.", config.key)
            }
            return
        }

        val collection = config.getCollectionRef()
        val batch = firestore.batch()

        var maxUpdatedAt: Long? = lastLocalSyncAt

        for (local in changedLocals) {
            val updatedAt = config.getUpdatedAt(local)
            val docId = config.getLocalId(local)
            val docRef = collection.document(docId)

            maxUpdatedAt = when {
                maxUpdatedAt == null -> updatedAt
                updatedAt > maxUpdatedAt!! -> updatedAt
                else -> maxUpdatedAt
            }

            if (config.isDeleted(local)) {
                // Hybrid strategy: write a tombstone flag instead of hard delete.
                val tombstone = mapOf(
                    "uid" to userId,
                    "id" to docId,
                    "isDeleted" to true,
                    "updatedAt" to updatedAt
                )
                batch.set(docRef, tombstone)
                if (syncConfig.loggingEnabled) {
                    Timber.tag(TAG).d("Local→Remote [%s] - tombstone docId=%s", config.key, docId)
                }
            } else {
                // Normal upsert: let toRemote() build full document body
                val data = config.toRemote(local, userId)
                batch.set(docRef, data)
                if (syncConfig.loggingEnabled) {
                    Timber.tag(TAG).d("Local→Remote [%s] - upsert docId=%s", config.key, docId)
                }
            }
        }

        // 3) Commit batch
        batch.commit().await()

        // 4) Persist updated checkpoint
        val newMeta = SyncMetadataEntity(
            key = config.key,
            lastLocalSyncAt = maxUpdatedAt,
            lastRemoteSyncAt = meta?.lastRemoteSyncAt
        )
        syncMetadataDao.upsert(newMeta)

        if (syncConfig.loggingEnabled) {
            Timber.tag(TAG).i(
                "Local→Remote [%s] - synced %d items, newLastLocalSyncAt=%s",
                config.key,
                changedLocals.size,
                maxUpdatedAt?.toString() ?: "null"
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
        // 1) Load existing metadata (checkpoint)
        val meta = syncMetadataDao.get(config.key)
        val lastRemoteSyncAt = meta?.lastRemoteSyncAt
        val lastLocalSyncAt = meta?.lastLocalSyncAt

        if (syncConfig.loggingEnabled) {
            Timber.tag(TAG).i(
                "Remote→Local [%s] - lastRemoteSyncAt=%s, lastLocalSyncAt=%s",
                config.key,
                lastRemoteSyncAt?.toString() ?: "null",
                lastLocalSyncAt?.toString() ?: "null"
            )
        }

        // 2) Build Firestore query with uid filter (hybrid model)
        var query: Query = config.getCollectionRef()
            .whereEqualTo("uid", userId)

        if (lastRemoteSyncAt != null) {
            query = query.whereGreaterThan("updatedAt", lastRemoteSyncAt)
        }

        // NOTE: For large datasets, you would add .limit(syncConfig.batchSize)
        // and paginate. For now, we fetch in one go.
        val snapshot = query.get().await()
        if (snapshot.isEmpty) {
            if (syncConfig.loggingEnabled) {
                Timber.tag(TAG).i("Remote→Local [%s] - no remote changes to sync.", config.key)
            }
            return
        }

        val toUpsert = mutableListOf<Local>()
        val toDeleteIds = mutableListOf<String>()
        var maxRemoteUpdatedAt: Long? = lastRemoteSyncAt

        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val updatedAt = (data["updatedAt"] as? Number)?.toLong()
            val isDeletedRemote = data["isDeleted"] as? Boolean ?: false
            val docId = doc.id

            if (updatedAt != null) {
                maxRemoteUpdatedAt = when {
                    maxRemoteUpdatedAt == null -> updatedAt
                    updatedAt > maxRemoteUpdatedAt!! -> updatedAt
                    else -> maxRemoteUpdatedAt
                }
            }

            // -------- ConflictStrategy handling (coarse-grained) --------
            val shouldApply = when (config.conflictStrategy) {
                ConflictStrategy.REMOTE_WINS -> {
                    // Always apply remote changes.
                    true
                }
                ConflictStrategy.LOCAL_WINS -> {
                    // Skip remote changes that are not strictly newer
                    // than local checkpoint.
                    if (lastLocalSyncAt == null || updatedAt == null) {
                        false
                    } else {
                        updatedAt > lastLocalSyncAt
                    }
                }
                ConflictStrategy.LATEST_UPDATED_WINS -> {
                    // Apply if remote is newer than the last local synced value.
                    if (lastLocalSyncAt == null || updatedAt == null) {
                        true
                    } else {
                        updatedAt >= lastLocalSyncAt
                    }
                }
            }

            if (!shouldApply) {
                if (syncConfig.loggingEnabled) {
                    Timber.tag(TAG).d(
                        "Remote→Local [%s] - skipping docId=%s due to conflict strategy.",
                        config.key,
                        docId
                    )
                }
                continue
            }

            if (isDeletedRemote) {
                toDeleteIds.add(docId)
            } else {
                val local = config.fromRemote(docId, data)
                toUpsert.add(local)
            }
        }

        // 3) Apply changes to Room
        if (toUpsert.isNotEmpty()) {
            config.daoAdapter.upsertAll(toUpsert)
        }
        if (toDeleteIds.isNotEmpty()) {
            config.daoAdapter.markDeletedByIds(toDeleteIds)
        }

        // 4) Persist updated checkpoint
        val newMeta = SyncMetadataEntity(
            key = config.key,
            lastLocalSyncAt = lastLocalSyncAt,
            lastRemoteSyncAt = maxRemoteUpdatedAt
        )
        syncMetadataDao.upsert(newMeta)

        if (syncConfig.loggingEnabled) {
            Timber.tag(TAG).i(
                "Remote→Local [%s] - upserted=%d, deleted=%d, newLastRemoteSyncAt=%s",
                config.key,
                toUpsert.size,
                toDeleteIds.size,
                maxRemoteUpdatedAt?.toString() ?: "null"
            )
        }
    }
}
