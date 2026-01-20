package com.example.eventreminder.sync.core

import com.example.eventreminder.data.session.SessionRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UserIdProvider — Session-backed UID provider
 *
 * Responsibilities:
 * - Provide the currently active app-owned UID
 * - Act as the single UID source for repositories & engines
 *
 * Architecture rules:
 * - MUST NOT touch FirebaseAuth
 * - MUST NOT retry or delay
 * - MUST reflect Splash-authenticated session only
 *
 * UID absence means AuthGate is NOT READY.
 */
fun interface UserIdProvider {
    suspend fun getUserId(): String?
}

/**
 * SessionUserIdProvider
 *
 * Reads UID from SessionRepository (DataStore).
 */
@Singleton
class SessionUserIdProvider @Inject constructor(
    private val sessionRepository: SessionRepository
) : UserIdProvider {

    override suspend fun getUserId(): String? {
        val session = sessionRepository.sessionState.first()
        val uid = session.uid

        if (uid == null) {
            Timber.tag("UserIdProvider")
                .e("❌ UID null from SessionRepository — AuthGate not READY [SessionUserIdProvider.kt::getUserId]")
        } else {
            Timber.tag("UserIdProvider")
                .d("UID resolved from session → $uid [SessionUserIdProvider.kt::getUserId]")
        }

        return uid
    }
}
