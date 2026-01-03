package com.example.eventreminder.sync.core

/*// =============================================================
// UserIdProvider — Robust Firebase UID Provider (UUID Sync Safe)
// -------------------------------------------------------------
// Responsibilities:
//   • Return currently authenticated user's UID
//   • Handle delayed FirebaseAuth restoration (OnePlus/Oppo issue)
//   • retry() wait loop (max 2 seconds)
//   • Avoid invalid reload() calls when user is null
//
// Why needed?
//   Some OEM devices restore FirebaseAuth user *after* process start.
//   Without retry, syncEngine sees null → skips sync.
//
// Project Standards:
//   • Named arguments ✓
//   • Section headers ✓
//   • Inline comments ✓
// =============================================================*/

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import timber.log.Timber

fun interface UserIdProvider {

    /**
     * Returns authenticated Firebase UID or null.
     */
    suspend fun getUserId(): String?

    companion object Factory {

        private const val TAG = "UserIdProvider"

        /**
         * Creates robust Firebase UID provider.
         */
        fun firebase(): UserIdProvider = UserIdProvider {

            val auth = FirebaseAuth.getInstance()

            // ---------------------------------------------------------
            // STEP 1: Immediate user check
            // ---------------------------------------------------------
            auth.currentUser?.uid?.let { uid ->
                Timber.tag(TAG).d("UID available immediately → $uid")
                return@UserIdProvider uid
            }

            Timber.tag(TAG).w("UID null → beginning recovery process")

            // ---------------------------------------------------------
            // STEP 2: Try reload ONLY if user object exists
            // ---------------------------------------------------------
            val currentUser = auth.currentUser
            if (currentUser != null) {
                try {
                    currentUser.reload().await()
                    currentUser.uid.let { uid ->
                        Timber.tag(TAG).d("UID restored after reload() → $uid")
                        return@UserIdProvider uid
                    }
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "reload() failed — continuing recovery loop")
                }
            } else {
                Timber.tag(TAG).w("reload() skipped → currentUser is null (cold start case)")
            }

            // ---------------------------------------------------------
            // STEP 3: Delayed Firebase auth restoration loop (2 sec max)
            // ---------------------------------------------------------
            val maxWaitMs = 2000L
            val startMs = System.currentTimeMillis()

            while (System.currentTimeMillis() - startMs < maxWaitMs) {
                val uid = auth.currentUser?.uid
                if (uid != null) {
                    Timber.tag(TAG).d("UID available after retry loop → $uid")
                    return@UserIdProvider uid
                }
                delay(100)
            }

            // ---------------------------------------------------------
            // STILL NULL — user is logged out or token invalidated
            // ---------------------------------------------------------
            Timber.tag(TAG).e("❌ UID STILL NULL after recovery loop — treating user as logged-out")
            return@UserIdProvider null
        }
    }
}
