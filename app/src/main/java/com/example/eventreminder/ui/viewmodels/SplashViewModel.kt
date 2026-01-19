package com.example.eventreminder.ui.viewmodels

// =============================================================
// Imports
// =============================================================
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.logging.AUTH_STATE_TAG
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import javax.inject.Inject

/**
 * SplashViewModel
 *
 * Responsibilities:
 * - Normalize DB illegal states
 * - Resolve FirebaseAuth state robustly (OEM delay safe)
 * - Decide AUTH GATE (no navigation here)
 *
 * Logging rule enforced:
 * Every log message ends with [FileName.kt::FunctionName]
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    // ============================================================
    // AUTH GATE — single source of navigation truth
    // ============================================================

    enum class AuthGate {
        LOGGED_OUT,
        EMAIL_UNVERIFIED,
        READY
    }

    private val _authGate = MutableStateFlow<AuthGate?>(null)
    val authGate: StateFlow<AuthGate?> = _authGate.asStateFlow()

    // ============================================================
    // ENTRY POINT FROM SplashScreen
    // ============================================================

    fun initialize() {
        viewModelScope.launch {

            Timber.tag(AUTH_STATE_TAG)
                .i("INIT → Splash initialization started [SplashViewModel.kt::initialize]")

            // ----------------------------------------------------
            // STEP 1: DB normalization (safe, local-only)
            // ----------------------------------------------------
            try {
                reminderRepository.normalizeRepeatRules()
                Timber.tag(AUTH_STATE_TAG)
                    .i("DB normalization completed [SplashViewModel.kt::initialize]")
            } catch (t: Throwable) {
                Timber.tag(AUTH_STATE_TAG)
                    .e(t, "DB normalization failed [SplashViewModel.kt::initialize]")
            }

            // ----------------------------------------------------
            // STEP 2: Resolve FirebaseAuth (OEM hydration safe)
            // ----------------------------------------------------
            //delay(5_000) // 🔬 EXPERIMENT: prove FirebaseAuth hydration delay

            val auth = FirebaseAuth.getInstance()

            var user = auth.currentUser
            var retries = 0

            while (user == null && retries < 20) {
                delay(150)
                retries++
                user = auth.currentUser

                Timber.tag(AUTH_STATE_TAG).d("Auth retry($retries) → user=$user " + "[SplashViewModel.kt::initialize]")
            }
            if (user != null) {
                try {
                    user.reload().await()   // 🔥 THIS IS REQUIRED
                    Timber.i(
                        "User reloaded email=${user.email} verified=${user.isEmailVerified} " +
                                "[SplashViewModel::initialize]"
                    )
                } catch (t: Throwable) {
                    Timber.e(
                        t,
                        "User reload failed [SplashViewModel::initialize]"
                    )
                }
            }

            // ----------------------------------------------------
            // STEP 3: Decide AuthGate
            // ----------------------------------------------------
            val gate = when {
                user == null -> {
                    Timber.tag(AUTH_STATE_TAG)
                        .i("STATE → LOGGED_OUT [SplashViewModel.kt::initialize]")
                    AuthGate.LOGGED_OUT
                }

                user.isEmailVerified.not() -> {
                    Timber.tag(AUTH_STATE_TAG)
                        .w(
                            "STATE → EMAIL_UNVERIFIED (${user.email}) " +
                                    "[SplashViewModel.kt::initialize]"
                        )
                    AuthGate.EMAIL_UNVERIFIED
                }

                else -> {
                    Timber.tag(AUTH_STATE_TAG)
                        .i(
                            "STATE → READY (uid=${user.uid}) " +
                                    "[SplashViewModel.kt::initialize]"
                        )
                    AuthGate.READY
                }
            }

            _authGate.value = gate
        }
    }
}
