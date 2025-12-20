package com.example.eventreminder.sync.core

// =============================================================
// Imports
// =============================================================
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import com.example.eventreminder.logging.DELETE_TAG

/**
 * SyncEngine
 *
 * Bi-directional synchronization engine for Firestore <-> Room.
 * Generic, works for any entity type through EntitySyncConfig.
 *
 * Soft delete model:
 * - Local delete -> Firestore tombstone { isDeleted = true }
 * - Firestore tombstone -> local markDeletedByIds()
 *
 * This engine is UI-agnostic.
 * It produces SyncResult which can be consumed by ViewModel / UI.
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
    /**
     * Runs full sync for all configured entities.
     *
     * @return SyncResult containing counts of created / updated / deleted / skipped
     *         records for both Local→Remote and Remote→Local directions.
     */
    suspend fun syncAll(): SyncResult {

        val result = SyncResult()

        if (syncConfig.loggingEnabled) {
            Timber.tag(TAG).i("Starting sync for %d entities", syncConfig.entities.size)
        }

        val userId = syncConfig.userIdProvider.getUserId()
        if (userId == null) {
            Timber.tag(TAG).w("Skipping sync: userId is null.")
            return result
        }

        Timber.tag(TAG).i("Sync using userId=%s", userId)

        for (raw in syncConfig.entities) {
            @Suppress("UNCHECKED_CAST")
            val config = raw as EntitySyncConfig<Any>

            try {
                when (config.direction) {
                    SyncDirection.LOCAL_TO_REMOTE ->
                        syncLocalToRemote(userId, config, result)

                    SyncDirection.REMOTE_TO_LOCAL ->
                        syncRemoteToLocal(userId, config, result)

                    SyncDirection.BIDIRECTIONAL -> {
                        syncLocalToRemote(userId, config, result)
                        syncRemoteToLocal(userId, config, result)
                    }
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Error syncing key=%s", config.key)
            }
        }

        // =============================================================
        // 🔥 FINAL SYNC SUMMARY (Log-only, UI can reuse SyncResult)
        // =============================================================
        Timber.tag(DELETE_TAG).i(
            "SYNC SUMMARY ↑C:%d ↑U:%d ↑D:%d ↑S:%d ↓C:%d ↓U:%d ↓D:%d ↓S:%d",
            result.localToRemoteCreated,
            result.localToRemoteUpdated,
            result.localToRemoteDeleted,
            result.localToRemoteSkipped,
            result.remoteToLocalCreated,
            result.remoteToLocalUpdated,
            result.remoteToLocalDeleted,
            result.remoteToLocalSkipped
        )

        if (syncConfig.loggingEnabled) {
            Timber.tag(TAG).i("Sync complete.")
        }

        return result
    }

    // =============================================================
    // Local → Remote
    // =============================================================
    /**
     * Local → Remote Sync
     *
     * STRATEGY:
     * - Push local changes to Firestore
     * - Push TOMBSTONES first (absolute priority)
     * - Skip terminal (disabled) records ONLY if not deleted
     * - NEVER overwrite an existing remote tombstone
     * - NEVER resurrect a deleted record on any device
     *
     * GOLDEN RULE:
     *   Deletion beats disable.
     *
     * WHY:
     * - One-time reminders are first disabled when fired
     * - Later they are converted into TOMBSTONES during cleanup
     * - Tombstones MUST be synced even if enabled=false
     * - Otherwise second device will resurrect stale remote records
     */
    private suspend fun <Local : Any> syncLocalToRemote(
        userId: String,
        config: EntitySyncConfig<Local>,
        result: SyncResult
    ) {
        val meta = syncMetadataDao.get(config.key)
        val lastLocalSyncAt = meta?.lastLocalSyncAt

        // Fetch all locally changed rows since last sync (includes deleted rows)
        val changedLocals = config.daoAdapter.getLocalsChangedAfter(lastLocalSyncAt)
        if (changedLocals.isEmpty()) return

        val batch = firestore.batch()
        val collection = config.getCollectionRef()
        var maxUpdatedAt = lastLocalSyncAt

        for (local in changedLocals) {

            val localUpdatedAt = config.getUpdatedAt(local)
            val docId = config.getLocalId(local)
            val docRef = collection.document(docId)

            if (maxUpdatedAt == null || localUpdatedAt > maxUpdatedAt) {
                maxUpdatedAt = localUpdatedAt
            }

            /*// ============================================================
            // 🔥 RULE #0 — LOCAL TOMBSTONE ALWAYS WINS (ABSOLUTE)
            //
            // If a record is marked isDeleted=true locally,
            // it MUST be written to remote regardless of enabled state.
            //
            // This guarantees:
            // - No remote orphan records
            // - No resurrection on second device
            // ============================================================*/
            if (config.isDeleted(local)) {
                result.localToRemoteDeleted++

                Timber.tag(DELETE_TAG).i("SYNC TOMBSTONE → id=%s updatedAt=%d", docId, localUpdatedAt)

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

            /*// ============================================================
            // 🔥 RULE #1 — TERMINAL (DISABLED) RECORDS NEVER UPSERT
            //
            // One-time reminders that have fired (enabled=false)
            // must NOT be re-uploaded to remote.
            //
            // They remain local-only until:
            // - Converted to tombstone in cleanup
            // - Then synced via RULE #0
            // ============================================================*/
            if (!config.enabled(local)) {
                result.localToRemoteSkipped++

                Timber.tag(DELETE_TAG).i("SYNC SKIP terminal record (enabled=false) id=%s", docId)
                continue
            }

            /*// ============================================================
            // RULE #2 — READ REMOTE STATE
            //
            // Remote tombstones are FINAL.
            // They can NEVER be overwritten or resurrected.
            // ============================================================*/
            val remoteSnapshot = docRef.get().await()
            val remoteData = remoteSnapshot.data
            val remoteDeleted = remoteData?.get("isDeleted") as? Boolean ?: false
            val remoteUpdatedAt = (remoteData?.get("updatedAt") as? Number)?.toLong()

            if (remoteDeleted) {
                result.localToRemoteSkipped++
                continue
            }

            if (remoteUpdatedAt != null && remoteUpdatedAt > localUpdatedAt) {
                result.localToRemoteSkipped++
                continue
            }

            // ============================================================
            // RULE #3 — NORMAL UPSERT
            // ============================================================
            if (remoteSnapshot.exists()) {
                result.localToRemoteUpdated++
            } else {
                result.localToRemoteCreated++
            }

            batch.set(
                docRef,
                config.toRemote(local, userId)
            )
        }

        batch.commit().await()

        // Advance sync checkpoint
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
        config: EntitySyncConfig<Local>,
        result: SyncResult
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

            // Local tombstone blocks remote resurrection
            if (config.daoAdapter.isLocalDeleted(docId)) {
                result.remoteToLocalSkipped++
                continue
            }

            // ============================================================
            // 🔥 RULE #1 — REMOTE TOMBSTONE
            // ============================================================
            if (isRemoteDeleted) {
                result.remoteToLocalDeleted++
                toDeleteIds.add(docId)
                continue
            }

            // ============================================================
            // RULE #2 — Conflict resolution
            // ============================================================
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

            if (!shouldApply) {
                result.remoteToLocalSkipped++
                continue
            }

            if (localUpdatedAt == null) {
                result.remoteToLocalCreated++
            } else {
                result.remoteToLocalUpdated++
            }

            if (remoteUpdatedAt != null &&
                (maxRemoteUpdatedAt == null || remoteUpdatedAt > maxRemoteUpdatedAt!!)
            ) {
                maxRemoteUpdatedAt = remoteUpdatedAt
            }

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
    private fun extractUpdatedAtMillis(raw: Any?): Long? =
        when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
}

/*
✅ SyncEngine.kt
💥 THE HEART OF THE ENTIRE SYNC SYSTEM

Now:
✔ Uses SyncResult as single source of truth
✔ Produces deterministic sync telemetry
✔ UI / Snackbar ready
✔ No architecture changes
✔ Multi-device & tombstone safe
*/
