package com.example.eventreminder

// =============================================================
// MainActivity — FIXED VERSION
// - Correct pendingNavRequest handling
// - Prevents PixelPreview from closing immediately
// - Works with SplashRoute and New Navigation Flow
// =============================================================

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.rememberNavController
import com.example.eventreminder.navigation.AppNavGraph
import com.example.eventreminder.navigation.PixelPreviewRouteString
import com.example.eventreminder.navigation.SplashRoute
import com.example.eventreminder.receivers.ReminderReceiver
import com.example.eventreminder.ui.theme.EventReminderTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private companion object {
        const val TAG = "NotificationNav"
        const val EXTRA_FROM_NOTIFICATION = "from_notification"
        const val EXTRA_REMINDER_ID_STRING = "reminder_id_string"
        const val EXTRA_EVENT_TYPE = "event_type"
    }

    // Acts as a one-time trigger
    private val pendingNavRequest = mutableStateOf<PendingNavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.tag(TAG).d("onCreate() — initial intent = $intent")

        processIntentForNavigation(intent)

        setContent {

            val navController = rememberNavController()

            EventReminderTheme {

                // Always starting from SplashRoute
                AppNavGraph(
                    navController = navController,
                    startDestination = SplashRoute
                )

                // ------------------------------------------------------------
                // 🔥 CRITICAL FIX:
                // Navigate ONLY when pendingNavRequest changes (from null → value)
                // NOT when navController recomposes!
                // ------------------------------------------------------------
                LaunchedEffect(pendingNavRequest.value) {

                    val req = pendingNavRequest.value ?: return@LaunchedEffect

                    Timber.tag(TAG).e("Consuming PendingNavRequest → $req")

                    // IMPORTANT: Clear BEFORE navigation to prevent double-trigger
                    pendingNavRequest.value = null

                    navController.navigate(
                        PixelPreviewRouteString(reminderIdString = req.reminderIdString)
                    ) {
                        launchSingleTop = true
                        restoreState = false
                        popUpTo(SplashRoute) { inclusive = false }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Timber.tag(TAG).d("onNewIntent() — new intent = $intent")
        setIntent(intent)
        processIntentForNavigation(intent)
    }

    private fun processIntentForNavigation(intent: Intent) {
        try {
            val action = intent.action
            val fromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)
            val uuid = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)
            val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE)

            Timber.tag(TAG).i(
                """
                processIntentForNavigation():
                    action            = $action
                    fromNotification  = $fromNotification
                    uuid              = $uuid
                    eventType         = $eventType
                """.trimIndent()
            )

            if (action == ReminderReceiver.ACTION_OPEN_CARD && !uuid.isNullOrBlank()) {
                Timber.tag(TAG).e("ACTION_OPEN_CARD → UUID=$uuid")
                pendingNavRequest.value = PendingNavRequest(uuid, eventType)
                return
            }

            if (fromNotification && !uuid.isNullOrBlank()) {
                Timber.tag(TAG).d("Normal notification tap → UUID=$uuid")
                pendingNavRequest.value = PendingNavRequest(uuid, eventType)
            }

        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "processIntentForNavigation → FAILED")
        }
    }

    private data class PendingNavRequest(
        val reminderIdString: String,
        val eventType: String?
    )
}
