package com.example.eventreminder

// =============================================================
// MainActivity — Handles Notification → Navigation (UUID Only)
// =============================================================
// Responsibilities:
//   • Read notification intent (tap OR Open Card action)
//   • Save UUID navigation request until NavController is ready
//   • Navigate to PixelPreviewRouteString safely
//
// Fixes added:
//   ✓ Explicit ACTION_OPEN_CARD handling
//   ✓ Extra logging for debugging navigation flow
//   ✓ Ensures pendingNavRequest fires exactly once
// =============================================================

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.rememberNavController
import com.example.eventreminder.navigation.AppNavGraph
import com.example.eventreminder.navigation.HomeGraphRoute
import com.example.eventreminder.navigation.LoginRoute
import com.example.eventreminder.navigation.PixelPreviewRouteString
import com.example.eventreminder.receivers.ReminderReceiver
import com.example.eventreminder.ui.theme.EventReminderTheme
import com.google.firebase.auth.FirebaseAuth
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

    // Holds pending navigation request (UUID-based)
    private val pendingNavRequest = mutableStateOf<PendingNavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.tag(TAG).d("onCreate() — initial intent = $intent")

        // Step 1: Process initial intent
        processIntentForNavigation(intent)

        setContent {
            val navController = rememberNavController()
            val isLoggedIn = FirebaseAuth.getInstance().currentUser != null

            val startDestination =
                if (isLoggedIn) HomeGraphRoute else LoginRoute

            EventReminderTheme {

                AppNavGraph(
                    navController = navController,
                    startDestination = startDestination
                )

                // Step 2: Consume pending navigation request
                LaunchedEffect(key1 = isLoggedIn, key2 = navController) {

                    val req = pendingNavRequest.value
                    if (req == null) {
                        Timber.tag(TAG).d("No pending navigation request")
                        return@LaunchedEffect
                    }

                    Timber.tag(TAG).w("PendingNavRequest detected → $req")

                    if (!isLoggedIn) {
                        Timber.tag(TAG).w("User not logged in → navigation aborted")
                        pendingNavRequest.value = null
                        return@LaunchedEffect
                    }

                    // Navigate to Pixel Card Editor
                    Timber.tag(TAG).e(
                        "Navigating NOW → PixelPreviewRouteString(UUID=${req.reminderIdString})"
                    )

                    navController.navigate(
                        PixelPreviewRouteString(
                            reminderIdString = req.reminderIdString
                        )
                    )

                    // Consume request (avoid reruns)
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
    // Intent Processor for Notification Navigation
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

            // Highest priority → Open Card action
            if (action == ReminderReceiver.ACTION_OPEN_CARD && !uuid.isNullOrBlank()) {
                Timber.tag(TAG).e("ACTION_OPEN_CARD detected → navigating to card UUID=$uuid")

                pendingNavRequest.value = PendingNavRequest(
                    reminderIdString = uuid,
                    eventType = eventType
                )
                return
            }

            // Normal tap on notification
            if (fromNotification && !uuid.isNullOrBlank()) {
                Timber.tag(TAG).d("Normal notification tap → UUID=$uuid")

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
