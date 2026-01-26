package com.example.eventreminder.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.eventreminder.logging.SHARE_LOGIN_TAG
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SessionRepository
 *
 * Caller(s):
 * - Login flow (on successful authentication)
 * - Logout flow (explicit user sign-out)
 * - SplashViewModel (read-only)
 * - HomeScreen (read-only, later)
 *
 * Responsibility:
 * - Persist app-owned session identity independent of FirebaseAuth.
 * - Act as the single source of truth for "which user the app considers active".
 *
 * Design Notes:
 * - Uses DataStore (Preferences), NOT encrypted storage.
 * - Survives OEM keystore invalidation issues (Samsung / OnePlus).
 * - Values are written ONLY on explicit login / logout.
 *
 * Stored Values:
 * - lastLoggedInUid
 * - lastLoggedInEmail
 */
@Singleton
class SessionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ------------------------------------------------------------
    // DataStore definition (app-wide)
    // ------------------------------------------------------------
    private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "app_session"
    )

    // ------------------------------------------------------------
    // Preference keys
    // ------------------------------------------------------------
    private object Keys {
        val LAST_LOGGED_IN_UID = stringPreferencesKey("last_logged_in_uid")
        val LAST_LOGGED_IN_EMAIL = stringPreferencesKey("last_logged_in_email")
    }

    // ------------------------------------------------------------
    // Public session stream
    // ------------------------------------------------------------

    /**
     * SessionState stream.
     *
     * Caller(s):
     * - SplashViewModel
     * - HomeScreen (future)
     *
     * Responsibility:
     * - Emits the last known app-owned session identity.
     *
     * Return:
     * - Flow emitting SessionState (uid/email may be null when logged out).
     */
    val sessionState: Flow<SessionState> =
        context.sessionDataStore.data.map { prefs ->
            SessionState(
                uid = prefs[Keys.LAST_LOGGED_IN_UID],
                email = prefs[Keys.LAST_LOGGED_IN_EMAIL]
            )
        }

    // ------------------------------------------------------------
    // Write operations (explicit only)
    // ------------------------------------------------------------

    /**
     * Persist successful login session.
     *
     * Caller(s):
     * - Login success handler
     *
     * Responsibility:
     * - Store UID and email as the app-owned active session.
     *
     * Return:
     * - Unit
     */
    suspend fun setLoggedIn(
        uid: String,
        email: String
    ) {
        context.sessionDataStore.edit { prefs ->
            prefs[Keys.LAST_LOGGED_IN_UID] = uid
            prefs[Keys.LAST_LOGGED_IN_EMAIL] = email
        }

        Timber.tag(SHARE_LOGIN_TAG).i("SESSION_SET uid=$uid email=$email [SessionRepository.kt::setLoggedIn]")
    }

    /**
     * Clear active session.
     *
     * Caller(s):
     * - Explicit logout flow
     *
     * Responsibility:
     * - Remove all app-owned session identity.
     *
     * Return:
     * - Unit
     */
    suspend fun clearSession() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(Keys.LAST_LOGGED_IN_UID)
            prefs.remove(Keys.LAST_LOGGED_IN_EMAIL)
        }

        Timber.tag(SHARE_LOGIN_TAG).i("SESSION_CLEARED [SessionRepository.kt::clearSession]")
    }
}

/**
 * SessionState
 *
 * Represents app-owned session identity.
 *
 * uid   = null → app considers user logged out
 * email = null → no user context available
 */
data class SessionState(
    val uid: String?,
    val email: String?
)
