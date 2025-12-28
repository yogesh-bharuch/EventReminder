package com.example.eventreminder.sync.core

// =============================================================
// Imports
// =============================================================
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * SyncEngine
 *
 * Bi-directional synchronization engine for Firestore <-> Room.
 *
 * HARD RULES:
 * - Sync NEVER runs if:
 *   • user is null
 *   • email is NOT verified
 *
 * This is a SECURITY + DATA-INTEGRITY gate.
 */
class SyncEngine(
    private val firestore: FirebaseFirestore,
    private val syncConfig: SyncConfig,
    private val syncMetadataDao: SyncMetadataDao
) {

    companion object {
        private const val TAG = "STATE"
    }

    // =============================================================
    // Sync Entry Point
    // =============================================================
    suspend fun syncAll(): SyncResult {

        val result = SyncResult()

        Timber.tag(TAG).i(
            "Sync requested [SyncEngine.kt::syncAll]"
        )

        // ---------------------------------------------------------
        // 🔐 AUTH HARD GATE (ABSOLUTE)
        // ---------------------------------------------------------
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            Timber.tag(TAG).w(
                "SYNC BLOCKED → user=null [SyncEngine.kt::syncAll]"
            )
            return result
        }

        if (!user.isEmailVerified) {
            Timber.tag(TAG).w(
                "SYNC BLOCKED → email not verified (${user.email}) [SyncEngine.kt::syncAll]"
            )
            return result
        }

        val userId = user.uid

        Timber.tag(TAG).i(
            "SYNC START → uid=$userId [SyncEngine.kt::syncAll]"
        )

        // ---------------------------------------------------------
        // ENTITY LOOP
        // ---------------------------------------------------------
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
                Timber.tag(TAG).e(
                    t,
                    "SYNC ERROR key=${config.key} [SyncEngine.kt::syncAll]"
                )
            }
        }

        Timber.tag(TAG).i(
            "SYNC COMPLETE ↑C:${result.localToRemoteCreated} ↑U:${result.localToRemoteUpdated} ↑D:${result.localToRemoteDeleted} " +
                    "↓C:${result.remoteToLocalCreated} ↓U:${result.remoteToLocalUpdated} ↓D:${result.remoteToLocalDeleted} " +
                    "[SyncEngine.kt::syncAll]"
        )

        return result
    }

    // =============================================================
    // Local → Remote
    // =============================================================
    private suspend fun <Local : Any> syncLocalToRemote(
        userId: String,
        config: EntitySyncConfig<Local>,
        result: SyncResult
    ) {
        Timber.tag(TAG).d(
            "Local→Remote START key=${config.key} [SyncEngine.kt::syncLocalToRemote]"
        )

        val meta = syncMetadataDao.get(config.key)
        val lastLocalSyncAt = meta?.lastLocalSyncAt

        val changedLocals =
            config.daoAdapter.getLocalsChangedAfter(lastLocalSyncAt)

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

            // -----------------------------------------------------
            // 🔥 LOCAL TOMBSTONE ALWAYS WINS
            // -----------------------------------------------------
            if (config.isDeleted(local)) {
                result.localToRemoteDeleted++

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

            // -----------------------------------------------------
            // TERMINAL RECORD SKIP
            // -----------------------------------------------------
            if (!config.enabled(local)) {
                result.localToRemoteSkipped++
                continue
            }

            val remoteSnapshot = docRef.get().await()
            val remoteData = remoteSnapshot.data
            val remoteDeleted =
                remoteData?.get("isDeleted") as? Boolean ?: false
            val remoteUpdatedAt =
                (remoteData?.get("updatedAt") as? Number)?.toLong()

            if (remoteDeleted) {
                result.localToRemoteSkipped++
                continue
            }

            if (remoteUpdatedAt != null && remoteUpdatedAt > localUpdatedAt) {
                result.localToRemoteSkipped++
                continue
            }

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
        config: EntitySyncConfig<Local>,
        result: SyncResult
    ) {
        Timber.tag(TAG).d(
            "Remote→Local START key=${config.key} [SyncEngine.kt::syncRemoteToLocal]"
        )

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

            if (config.daoAdapter.isLocalDeleted(docId)) {
                result.remoteToLocalSkipped++
                continue
            }

            if (isRemoteDeleted) {
                result.remoteToLocalDeleted++
                toDeleteIds.add(docId)
                continue
            }

            val shouldApply =
                lastRemoteSyncAt == null ||
                        (remoteUpdatedAt != null && remoteUpdatedAt > lastRemoteSyncAt)

            if (!shouldApply) {
                result.remoteToLocalSkipped++
                continue
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
    // Helpers
    // =============================================================
    private fun extractUpdatedAtMillis(raw: Any?): Long? =
        when (raw) {
            is Number -> raw.toLong()
            is String -> raw.toLongOrNull()
            else -> null
        }
}
