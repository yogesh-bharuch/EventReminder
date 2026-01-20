package com.example.eventreminder.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.data.session.SessionRepository
import com.example.eventreminder.data.session.SessionState
import com.example.eventreminder.logging.AUTH_STATE_TAG
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

/**
 * SplashViewModel
 *
 * SOURCE OF TRUTH:
 * 1. SessionRepository (DataStore)
 * 2. FirebaseAuth (bootstrap only)
 *
 * Rules:
 * - If session exists → do NOT consult FirebaseAuth
 * - FirebaseAuth is used ONLY to bootstrap a missing session
 * - Session is written ONLY after verified Firebase login
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    // ------------------------------------------------------------
    // Session (read-only, UI-safe)
    // ------------------------------------------------------------
    val sessionState: StateFlow<SessionState?> =
        sessionRepository.sessionState
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null
            )


    // ============================================================
    // AUTH GATE
    // ============================================================

    enum class AuthGate {
        LOGGED_OUT,
        EMAIL_UNVERIFIED,
        READY
    }

    private val _authGate = MutableStateFlow<AuthGate?>(null)
    val authGate: StateFlow<AuthGate?> = _authGate.asStateFlow()

    // ============================================================
    // ENTRY POINT
    // ============================================================

    fun initialize() {
        viewModelScope.launch {

            Timber.tag(AUTH_STATE_TAG).i("INIT → Splash initialization started [SplashViewModel.kt::initialize]")

            // ----------------------------------------------------
            // STEP 1: Check app-owned session FIRST
            // ----------------------------------------------------
            val session = sessionRepository.sessionState.first()

            if (session.uid != null && session.email != null) {
                Timber.tag(AUTH_STATE_TAG).i("Session present uid=${session.uid} email=${session.email} " + "[SplashViewModel.kt::initialize]")

                // Session already trusted → READY directly
                _authGate.value = AuthGate.READY
                normalizeRepeatRules()
                // 🔑 OPTION B — one-shot FirebaseAuth rehydration
                attemptFirebaseRehydrateOnce()
                return@launch
            }

            // No session found
            Timber.tag(AUTH_STATE_TAG).i("No session found → checking FirebaseAuth [SplashViewModel.kt::initialize]")

            // ----------------------------------------------------
            // STEP 2: FirebaseAuth bootstrap (ONLY when no session)
            // ----------------------------------------------------
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser

            if (user == null) {
                Timber.tag(AUTH_STATE_TAG).i("STATE → LOGGED_OUT (no Firebase user) [SplashViewModel.kt::initialize]")

                _authGate.value = AuthGate.LOGGED_OUT
                return@launch
            }

            try {
                user.reload().await()
            } catch (t: Throwable) {
                Timber.tag(AUTH_STATE_TAG).e(t, "User reload failed [SplashViewModel.kt::initialize]")
            }

            if (!user.isEmailVerified) {
                Timber.tag(AUTH_STATE_TAG).w("STATE → EMAIL_UNVERIFIED (${user.email}) " + "[SplashViewModel.kt::initialize]")

                _authGate.value = AuthGate.EMAIL_UNVERIFIED
                return@launch
            }

            updateDataStore(user)

            Timber.tag(AUTH_STATE_TAG).i("STATE → READY (uid=${user.uid}) [SplashViewModel.kt::initialize]")

            _authGate.value = AuthGate.READY
        }
    }

    // ============================================================
    // OPTION B — FirebaseAuth rehydration (ONE-SHOT)
    // ============================================================

    private fun attemptFirebaseRehydrateOnce() {
        viewModelScope.launch {

            val auth = FirebaseAuth.getInstance()

            if (auth.currentUser != null) {
                Timber.tag(AUTH_STATE_TAG).d("FirebaseAuth already available → skip rehydrate [SplashViewModel.kt::attemptFirebaseRehydrateOnce]")
                return@launch
            }

            Timber.tag(AUTH_STATE_TAG).i("Attempting FirebaseAuth rehydration (one-shot) [SplashViewModel.kt::attemptFirebaseRehydrateOnce]")

            try {
                auth.currentUser?.reload()?.await()
                Timber.tag(AUTH_STATE_TAG).i("FirebaseAuth rehydrate attempt finished user=${auth.currentUser?.uid} " + "[SplashViewModel.kt::attemptFirebaseRehydrateOnce]")
            } catch (t: Throwable) {
                Timber.tag(AUTH_STATE_TAG).w(t, "FirebaseAuth rehydration failed (ignored) [SplashViewModel.kt::attemptFirebaseRehydrateOnce]")
            }
        }
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private suspend fun updateDataStore(user: FirebaseUser) {
        sessionRepository.setLoggedIn(
            uid = user.uid,
            email = user.email.orEmpty()
        )

        Timber.tag(AUTH_STATE_TAG).i("Session persisted uid=${user.uid} [SplashViewModel.kt::persistSession]")

        // Optional / deferred
        try {
            // reminderRepository.normalizeRepeatRules()
        } catch (_: Throwable) {
        }
    }

    private suspend fun normalizeRepeatRules() {
        try {
            reminderRepository.normalizeRepeatRules()
        } catch (_: Throwable) {
        }
    }

    /**
     * Logout
     *
     * Caller:
     * - HomeScreen
     * - EmailVerificationScreen (if needed)
     *
     * Responsibility:
     * - Clear FirebaseAuth
     * - Clear app-owned session
     * - Reset auth gate
     */
    fun logout() {
        viewModelScope.launch {

            Timber.tag(AUTH_STATE_TAG).i("🚪 Logout requested [SplashViewModel.kt::logout]")

            // 1️⃣ Clear FirebaseAuth (cloud capability)
            FirebaseAuth.getInstance().signOut()

            // 2️⃣ Clear app-owned session (AUTHORITATIVE)
            sessionRepository.clearSession()

            // 3️⃣ Reset gate (defensive)
            _authGate.value = AuthGate.LOGGED_OUT

            Timber.tag(AUTH_STATE_TAG).i("✅ Logout completed (Firebase + Session cleared) [SplashViewModel.kt::logout]")
        }
    }


}
