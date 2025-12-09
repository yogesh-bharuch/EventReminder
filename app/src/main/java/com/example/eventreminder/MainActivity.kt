package com.example.eventreminder

// =============================================================
// MainActivity — Notification → Navigation (UUID)
// + Integrated with new SplashRoute + Battery Optimization Flow
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

    // This holds UUID navigation requests until NavController is ready.
    private val pendingNavRequest = mutableStateOf<PendingNavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.tag(TAG).d("onCreate() — initial intent = $intent")

        // Extract notification navigation request
        processIntentForNavigation(intent)

        setContent {
            val navController = rememberNavController()

            EventReminderTheme {

                // 🔥 ALWAYS START FROM SPLASH
                AppNavGraph(
                    navController = navController,
                    startDestination = SplashRoute
                )

                // ------------------------------------------------------------
                // AFTER Splash decides login → handle pending PixelPreview nav
                // ------------------------------------------------------------
                LaunchedEffect(navController) {

                    val req = pendingNavRequest.value
                    if (req == null) {
                        Timber.tag(TAG).d("No pending navigation request")
                        return@LaunchedEffect
                    }

                    Timber.tag(TAG).w("PendingNavRequest detected → $req")
                    Timber.tag(TAG).e("Navigating → PixelPreviewRouteString(UUID=${req.reminderIdString})")

                    navController.navigate(
                        PixelPreviewRouteString(
                            reminderIdString = req.reminderIdString
                        )
                    )

                    // Consume request to avoid double navigation
                    pendingNavRequest.value = null
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

    // =============================================================
    // Extract Notification → Pixel Card Navigation Requests
    // =============================================================
    private fun processIntentForNavigation(intent: Intent) {
        try {
            val action = intent.action
            val fromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)
            val uuid = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)
            val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE)

            Timber.tag(TAG).i(
                """
                processIntentForNavigation():
                    action         = $action
                    fromNotif      = $fromNotification
                    uuid           = $uuid
                    eventType      = $eventType
                """.trimIndent()
            )

            // Highest priority → Open Card Quick Action
            if (action == ReminderReceiver.ACTION_OPEN_CARD && !uuid.isNullOrBlank()) {
                Timber.tag(TAG).e("ACTION_OPEN_CARD → Navigate to card UUID=$uuid")

                pendingNavRequest.value = PendingNavRequest(
                    reminderIdString = uuid,
                    eventType = eventType
                )
                return
            }

            // Simple notification tap
            if (fromNotification && !uuid.isNullOrBlank()) {
                Timber.tag(TAG).d("Notification tap → UUID=$uuid")

                pendingNavRequest.value = PendingNavRequest(
                    reminderIdString = uuid,
                    eventType = eventType
                )
            }

        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "processIntentForNavigation → FAILED")
        }
    }

    // =============================================================
    // Pending Navigation Holder (UUID)
    // =============================================================
    private data class PendingNavRequest(
        val reminderIdString: String,
        val eventType: String?
    )
}
