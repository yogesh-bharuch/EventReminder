package com.example.eventreminder.sync.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val syncConfig: SyncConfig,
    private val syncMetadataDao: SyncMetadataDao
) {

    companion object {
        private const val TAG = "STATE"
        private const val TAG1 = "PROB_SYNC"
    }

    // =============================================================
    // Sync Entry Point
    // =============================================================
    suspend fun syncAll(): SyncResult {

        val result = SyncResult()
        var networkFailure = false   // ⭐ NEW

        Timber.tag(TAG).i("Sync requested [SyncEngine.kt::syncAll]")

        // 🔐 AUTH HARD GATE
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            result.blockedReason = SyncBlockedReason.USER_NOT_LOGGED_IN
            return result
        }

        if (!user.isEmailVerified) {
            result.blockedReason = SyncBlockedReason.EMAIL_NOT_VERIFIED
            return result
        }

        // 🌐 INTERNET HARD GATE (NEW — REQUIRED)
        if (!hasInternet()) {
            Timber.tag(TAG1).w("SYNC BLOCKED → no internet [SyncEngine.kt::syncAll]")
            result.blockedReason = SyncBlockedReason.NO_INTERNET
            return result
        }

        val userId = user.uid
        Timber.tag(TAG).i("SYNC START → uid=$userId [SyncEngine.kt::syncAll]")

        // ENTITY LOOP
        for (raw in syncConfig.entities) {
            @Suppress("UNCHECKED_CAST")
            val config = raw as EntitySyncConfig<Any>

            try {
                when (config.direction) {
                    SyncDirection.LOCAL_TO_REMOTE -> syncLocalToRemote(userId, config, result)

                    SyncDirection.REMOTE_TO_LOCAL -> syncRemoteToLocal(userId, config, result)

                    SyncDirection.BIDIRECTIONAL -> {
                        syncLocalToRemote(userId, config, result)
                        syncRemoteToLocal(userId, config, result)
                    }
                }
            } catch (t: Throwable) {

                // 🌐 Network failure detection (single source of truth)
                if (
                    t is java.net.UnknownHostException ||
                    t is java.net.SocketTimeoutException
                ) {
                    networkFailure = true
                }

                Timber.tag(TAG).e(t, "SYNC ERROR key=${config.key} [SyncEngine.kt::syncAll]")
            }
        }

        // ⭐ FINAL BLOCK DECISION
        if (networkFailure) {
            result.blockedReason = SyncBlockedReason.NO_INTERNET
        }

        Timber.tag(TAG).i("SYNC COMPLETE ↑C:${result.localToRemoteCreated} ↑U:${result.localToRemoteUpdated} ↑D:${result.localToRemoteDeleted} " + "↓C:${result.remoteToLocalCreated} ↓U:${result.remoteToLocalUpdated} ↓D:${result.remoteToLocalDeleted}")

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
        Timber.tag(TAG1).d("▶️ L2R ENTER key=${config.key}")
        Timber.tag(TAG).d("Local→Remote START key=${config.key} [SyncEngine.kt::syncLocalToRemote]")

        val meta = syncMetadataDao.get(config.key)
        val lastLocalSyncAt = meta?.lastLocalSyncAt
        Timber.tag(TAG1).d("L2R lastLocalSyncAt=$lastLocalSyncAt")

        val changedLocals = config.daoAdapter.getLocalsChangedAfter(lastLocalSyncAt)
        Timber.tag(TAG1).d("L2R changedLocals.size=${changedLocals.size}")

        if (changedLocals.isEmpty()) return

        val batch = firestore.batch()
        val collection = config.getCollectionRef()
        var maxUpdatedAt = lastLocalSyncAt

        for ((index, local) in changedLocals.withIndex()) {
            Timber.tag(TAG1).d("➡️ L2R[$index] START")

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
                Timber.tag(TAG1).d("L2R[$index] isDeleted=true → batch.set")
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

            // -----------------------------------------------------
            // 🌐 NETWORK READ (GUARDED)
            // -----------------------------------------------------
            Timber.tag(TAG1).d("🌐 L2R[$index] BEFORE docRef.get()")
            val remoteSnapshot = try {
                docRef.get().await()
            } catch (t: Throwable) {
                if (t is java.net.UnknownHostException || t is java.net.SocketTimeoutException) {
                    Timber.tag(TAG1).w("🌐 L2R[$index] network down → abort L2R")
                    result.blockedReason = SyncBlockedReason.NO_INTERNET
                    return
                }
                throw t
            }
            Timber.tag(TAG1).d("🌐 L2R[$index] AFTER docRef.get() exists=${remoteSnapshot.exists()}")

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

        // -----------------------------------------------------
        // 🌐 NETWORK WRITE (GUARDED)
        // -----------------------------------------------------
        Timber.tag(TAG1).d("🌐 L2R BEFORE batch.commit()")
        try {
            batch.commit().await()
        } catch (t: Throwable) {
            if (t is java.net.UnknownHostException || t is java.net.SocketTimeoutException) {
                Timber.tag(TAG1).w("🌐 L2R network down during commit → abort")
                result.blockedReason = SyncBlockedReason.NO_INTERNET
                return
            }
            throw t
        }
        Timber.tag(TAG1).d("🌐 L2R AFTER batch.commit()")

        if (maxUpdatedAt != meta?.lastLocalSyncAt) {
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    key = config.key,
                    lastLocalSyncAt = maxUpdatedAt,
                    lastRemoteSyncAt = meta?.lastRemoteSyncAt
                )
            )
            Timber.tag(TAG1).d("L2R cursor advanced → $maxUpdatedAt")
        }

        Timber.tag(TAG1).d("✅ L2R EXIT key=${config.key}")
    }


    // =============================================================
    // Remote → Local
    // =============================================================
    private suspend fun <Local : Any> syncRemoteToLocal(
        userId: String,
        config: EntitySyncConfig<Local>,
        result: SyncResult
    ) {
        Timber.tag(TAG1).d("▶️ R2L ENTER key=${config.key}")
        Timber.tag(TAG).d("Remote→Local START key=${config.key} [SyncEngine.kt::syncRemoteToLocal]")

        val meta = syncMetadataDao.get(config.key)
        val lastRemoteSyncAt = meta?.lastRemoteSyncAt
        val lastLocalSyncAt = meta?.lastLocalSyncAt

        // -----------------------------------------------------
        // 🌐 NETWORK READ (GUARDED)
        // -----------------------------------------------------
        Timber.tag(TAG1).d("🌐 R2L BEFORE collection.get()")
        val snapshot = try {
            config.getCollectionRef()
                .whereEqualTo("uid", userId)
                .limit(500)
                .get()
                .await()
        } catch (t: Throwable) {
            if (isNetworkThrowable(t)) {
                Timber.tag(TAG1).w("🌐 R2L network down → abort R2L")
                result.blockedReason = SyncBlockedReason.NO_INTERNET
                return
            }
            throw t
        }
        Timber.tag(TAG1).d("🌐 R2L AFTER collection.get() size=${snapshot.size()}")

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

            if (
                remoteUpdatedAt != null &&
                (maxRemoteUpdatedAt == null || remoteUpdatedAt > maxRemoteUpdatedAt!!)
            ) {
                maxRemoteUpdatedAt = remoteUpdatedAt
            }

            toUpsert.add(config.fromRemote(docId, data))
        }

        // -----------------------------------------------------
        // LOCAL DB WRITE (SAFE — NO NETWORK)
        // -----------------------------------------------------
        if (toUpsert.isNotEmpty()) {
            config.daoAdapter.upsertAll(toUpsert)
        }

        if (toDeleteIds.isNotEmpty()) {
            config.daoAdapter.markDeletedByIds(toDeleteIds)
        }

        // -----------------------------------------------------
        // METADATA UPDATE (ONLY IF FULL SUCCESS)
        // -----------------------------------------------------
        if (maxRemoteUpdatedAt != lastRemoteSyncAt) {
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    key = config.key,
                    lastLocalSyncAt = lastLocalSyncAt,
                    lastRemoteSyncAt = maxRemoteUpdatedAt
                )
            )
            Timber.tag(TAG1).d("R2L cursor advanced → $maxRemoteUpdatedAt")
        }

        Timber.tag(TAG1).d("✅ R2L EXIT key=${config.key}")
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

    private fun isNetworkThrowable(t: Throwable): Boolean =
        t is java.net.UnknownHostException ||
                t is java.net.SocketTimeoutException

    private fun hasInternet(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }


}
