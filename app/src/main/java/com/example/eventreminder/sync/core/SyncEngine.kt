package com.example.eventreminder.sync.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import com.example.eventreminder.logging.SYNC_TAG
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


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

    /*companion object {
        private const val TAG = "STATE"
        private const val TAG1 = "PROB_SYNC"
    }*/

    // =============================================================
    // Sync Entry Point
    // =============================================================
    suspend fun syncAll(): SyncResult {

        val result = SyncResult()
        var networkFailure = false   // ⭐ NEW

        //Timber.tag(SYNC_TAG).i("Sync requested [SyncEngine.kt::syncAll]")

        // 🔐 AUTH HARD GATE
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            result.blockedReason = SyncBlockedReason.USER_NOT_LOGGED_IN
            Timber.tag(SYNC_TAG).w("SYNC BLOCKED → no internet [SyncEngine.kt::syncAll]")
            return result
        }

        if (!user.isEmailVerified) {
            result.blockedReason = SyncBlockedReason.EMAIL_NOT_VERIFIED
            Timber.tag(SYNC_TAG).w("SYNC BLOCKED → E-mail not varified [SyncEngine.kt::syncAll]")
            return result
        }

        // 🌐 INTERNET HARD GATE (NEW — REQUIRED)
        if (!hasInternet()) {
            Timber.tag(SYNC_TAG).w("SYNC BLOCKED → no internet [SyncEngine.kt::syncAll]")
            result.blockedReason = SyncBlockedReason.NO_INTERNET
            return result
        }

        val userId = user.uid
        //Timber.tag(SYNC_TAG).i("SYNC START → uid=$userId [SyncEngine.kt::syncAll]")

        // ENTITY LOOP
        for (raw in syncConfig.entities) {
            @Suppress("UNCHECKED_CAST")
            val config = raw as EntitySyncConfig<Any>

            try {
                when (config.direction) {

                    SyncDirection.LOCAL_TO_REMOTE -> syncLocalToRemote(userId, config, result)

                    SyncDirection.REMOTE_TO_LOCAL -> syncRemoteToLocal(userId, config, result)

                    SyncDirection.BIDIRECTIONAL -> {
                        Timber.tag(SYNC_TAG).i("Bi_direction Sync requested [SyncEngine.kt::syncAll]")
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

                Timber.tag(SYNC_TAG).e(t, "SYNC ERROR key=${config.key} [SyncEngine.kt::syncAll]")
            }
        }

        // ⭐ FINAL BLOCK DECISION
        if (networkFailure) {
            result.blockedReason = SyncBlockedReason.NO_INTERNET
        }

        //Timber.tag(SYNC_TAG).i("SYNC COMPLETE ↑C:${result.localToRemoteCreated} ↑U:${result.localToRemoteUpdated} ↑D:${result.localToRemoteDeleted} " + "↓C:${result.remoteToLocalCreated} ↓U:${result.remoteToLocalUpdated} ↓D:${result.remoteToLocalDeleted} [SyncEngine.kt::syncAll]")

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
        Timber.tag(SYNC_TAG).i("▶️ L2R Started for key=${config.key} [SyncEngine.kt::syncLocalToRemote]")

        val meta = syncMetadataDao.get(config.key)
        val lastLocalSyncAt = meta?.lastLocalSyncAt
        //Timber.tag(SYNC_TAG).d("L2R lastLocalSyncAt=$lastLocalSyncAt: ${formatEpochMillis(lastLocalSyncAt)} Hrs. [SyncEngine.kt::syncLocalToRemote]")

        val changedLocals = config.daoAdapter.getLocalsChangedAfter(lastLocalSyncAt)
        Timber.tag(SYNC_TAG).d("L2R ${changedLocals.size ?: 0} updated records found. after lastLocalSyncAt= ${formatEpochMillis(lastLocalSyncAt)} Hrs. ($lastLocalSyncAt). [SyncEngine.kt::syncLocalToRemote]")

        if (changedLocals.isEmpty()) return

        val batch = firestore.batch()
        val collection = config.getCollectionRef()
        var maxUpdatedAt = lastLocalSyncAt

        for ((index, local) in changedLocals.withIndex()) {
            //Timber.tag(SYNC_TAG).d("➡️ L2R[$index] START. [SyncEngine.kt::syncLocalToRemote]")

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
                //Timber.tag(SYNC_TAG).d("L2R[$index] isDeleted=true → batch.set. [SyncEngine.kt::syncLocalToRemote]")
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
            //Timber.tag(SYNC_TAG).d("🌐 L2R[$index] BEFORE docRef.get(). [SyncEngine.kt::syncLocalToRemote]")
            val remoteSnapshot = try {
                docRef.get().await()
            } catch (t: Throwable) {
                if (t is java.net.UnknownHostException || t is java.net.SocketTimeoutException) {
                    Timber.tag(SYNC_TAG).w("🌐 L2R[$index] network down → abort L2R. [SyncEngine.kt::syncLocalToRemote]")
                    result.blockedReason = SyncBlockedReason.NO_INTERNET
                    return
                }
                throw t
            }
            //Timber.tag(SYNC_TAG).d("🌐 L2R[$index] AFTER docRef.get() exists=${remoteSnapshot.exists()}. [SyncEngine.kt::syncLocalToRemote]")

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
        //Timber.tag(SYNC_TAG).d("🌐 L2R BEFORE batch.commit()")
        try {
            batch.commit().await()
        } catch (t: Throwable) {
            if (t is java.net.UnknownHostException || t is java.net.SocketTimeoutException) {
                Timber.tag(SYNC_TAG).w("🌐 L2R network down during commit → abort. [SyncEngine.kt::syncLocalToRemote]")
                result.blockedReason = SyncBlockedReason.NO_INTERNET
                return
            }
            throw t
        }
        //Timber.tag(SYNC_TAG).d("🌐 L2R AFTER batch.commit()")

        if (maxUpdatedAt != meta?.lastLocalSyncAt) {
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    key = config.key,
                    lastLocalSyncAt = maxUpdatedAt,
                    lastRemoteSyncAt = meta?.lastRemoteSyncAt
                )
            )
            Timber.tag(SYNC_TAG).d("L2R syncMetadata table updated for ${config.key} lastLocalSyncAt → ${formatEpochMillis(maxUpdatedAt)} ($maxUpdatedAt). [SyncEngine.kt::syncLocalToRemote]")

        }

        Timber.tag(SYNC_TAG).d("✅ L2R Sync Completed for key=${config.key}. [SyncEngine.kt::syncLocalToRemote]")
    }


    // =============================================================
    // Remote → Local
    // =============================================================
    private suspend fun <Local : Any> syncRemoteToLocal(
        userId: String,
        config: EntitySyncConfig<Local>,
        result: SyncResult
    ) {
        Timber.tag(SYNC_TAG).d("▶️ R2L Started for key=${config.key}. [SyncEngine.kt::syncRemoteToLocal]")

        val meta = syncMetadataDao.get(config.key)
        val lastRemoteSyncAt = meta?.lastRemoteSyncAt
        val lastLocalSyncAt = meta?.lastLocalSyncAt

        // -----------------------------------------------------
        // 🌐 NETWORK READ (FIRESTORE-OPTIMIZED)
        // -----------------------------------------------------
        //Timber.tag(SYNC_TAG).d("🌐 R2L BEFORE collection.get(). [SyncEngine.kt::syncRemoteToLocal]")
        //val TEST_UID = "ynyAQJRDHddkI2FNgGcGAnE1Puy2"//"Gg7j2KWTgHT1PsOUAevovZrn9bo1"
        //val TEST_UPDATED_AT = 1768883054892L
        val snapshot = try {
            config.getCollectionRef()
                .whereEqualTo("uid", userId)
                .whereGreaterThan("updatedAt", lastRemoteSyncAt ?: 0L)
                .orderBy("updatedAt")
                .limit(500)
                .get()
                .await()
        } catch (t: Throwable) {
            if (isNetworkThrowable(t)) {
                Timber.tag(SYNC_TAG).w("🌐 R2L network down → abort R2L [SyncEngine.kt::syncRemoteToLocal]")
                result.blockedReason = SyncBlockedReason.NO_INTERNET
                return
            }
            throw t
        }

        Timber.tag(SYNC_TAG).d("R2L ${snapshot.size()} updated records found. after lastRemoteSyncAt ${formatEpochMillis(lastRemoteSyncAt)} Hrs. ($lastRemoteSyncAt) [SyncEngine.kt::syncRemoteToLocal]")

        if (snapshot.isEmpty) return

        val toUpsert = mutableListOf<Local>()
        val toDeleteIds = mutableListOf<String>()
        var maxRemoteUpdatedAt = lastRemoteSyncAt

        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val docId = doc.id

            val remoteUpdatedAt = extractUpdatedAtMillis(data["updatedAt"])
            val isRemoteDeleted = data["isDeleted"] as? Boolean ?: false

            // -----------------------------------------------------
            // LOCAL TOMBSTONE GUARD
            // -----------------------------------------------------
            if (config.daoAdapter.isLocalDeleted(docId)) {
                result.remoteToLocalSkipped++
                continue
            }

            // -----------------------------------------------------
            // REMOTE DELETE
            // -----------------------------------------------------
            if (isRemoteDeleted) {
                result.remoteToLocalDeleted++
                toDeleteIds.add(docId)
                continue
            }

            // -----------------------------------------------------
            // HIGH-WATER MARK UPDATE
            // -----------------------------------------------------
            if (
                remoteUpdatedAt != null &&
                (maxRemoteUpdatedAt == null || remoteUpdatedAt > maxRemoteUpdatedAt)
            ) {
                maxRemoteUpdatedAt = remoteUpdatedAt
            }

            // -----------------------------------------------------
            // CREATE vs UPDATE COUNT
            // -----------------------------------------------------
            val localUpdatedAt = config.daoAdapter.getLocalUpdatedAt(docId)
            if (localUpdatedAt != null) {
                result.remoteToLocalUpdated++
            } else {
                result.remoteToLocalCreated++
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
        // METADATA UPDATE (ONLY AFTER FULL SUCCESS)
        // -----------------------------------------------------
        if (maxRemoteUpdatedAt != lastRemoteSyncAt) {
            syncMetadataDao.upsert(
                SyncMetadataEntity(
                    key = config.key,
                    lastLocalSyncAt = lastLocalSyncAt,
                    lastRemoteSyncAt = maxRemoteUpdatedAt
                )
            )
            Timber.tag(SYNC_TAG).d("R2L syncMetadata table updated for syncTime\n lastRemoteSyncAt=${formatEpochMillis(maxRemoteUpdatedAt)} ($maxRemoteUpdatedAt)\n lastLocalSyncAt=${formatEpochMillis(lastLocalSyncAt)} ($lastLocalSyncAt) " + "[SyncEngine.kt::syncRemoteToLocal]")

            //Timber.tag(SYNC_TAG).d("R2L syncMetadata updated lastRemoteSyncAt=${formatEpochMillis(maxRemoteUpdatedAt)} ($maxRemoteUpdatedAt) [SyncEngine.kt::syncRemoteToLocal]")
        }

        // created: updated  deleted  skipped
        //Timber.tag(SYNC_TAG).d("✅ R2L Completed for key=${config.key}\n [created=${result.remoteToLocalCreated}\n updated=${result.remoteToLocalUpdated}\n deleted=${result.remoteToLocalDeleted}\n skipped=${result.remoteToLocalSkipped}]\n after=${formatEpochMillis(lastRemoteSyncAt)} ($lastRemoteSyncAt) [SyncEngine.kt::syncRemoteToLocal]")
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

    private fun formatEpochMillis(epochMillis: Long?): String {
        if (epochMillis == null) return "—"
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())   // Asia/Kolkata implicitly
            .format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))
    }

}
