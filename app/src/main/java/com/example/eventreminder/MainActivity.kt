package com.example.eventreminder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import com.example.eventreminder.navigation.*
import com.example.eventreminder.ui.theme.EventReminderTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

// =============================================================
// MainActivity
// =============================================================
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private companion object {
        const val TAG = "NotificationTest"
    }

    // Hold a pending navigation request when an intent comes in before NavController is ready.
    private val pendingNavRequest = mutableStateOf<PendingNavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Process initial intent into pending navigation request (if any)
        processIntentForNavigation(intent)

        setContent {
            val navController = rememberNavController()

            // Determine if user is logged in
            val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
            val start = if (isLoggedIn) HomeRoute else LoginRoute

            EventReminderTheme {
                AppNavGraph(
                    navController = navController,
                    startDestination = start
                )

                // When NavController and UI are ready, execute any pending navigation.
                LaunchedEffect(isLoggedIn, navController) {
                    val req = pendingNavRequest.value
                    if (req != null) {
                        if (isLoggedIn) {
                            Timber.tag(TAG).d("Handling pending nav → navigating to CardScreen(reminderId=${req.reminderId})")
                            navController.navigate(CardRoute(reminderId = req.reminderId))
                            // clear pending request to avoid re-navigation on config changes
                            pendingNavRequest.value = null
                        } else {
                            Timber.tag(TAG).w("Received notification nav but user not logged in — skipping navigation")
                            pendingNavRequest.value = null
                        }
                    }
                }
            }
        }
    }

    // =============================================================
    // Handle intents delivered when activity already exists (notification taps)
    // =============================================================
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // Update stored intent
        setIntent(intent)

        // Process navigation extras again
        processIntentForNavigation(intent)
    }

    // =============================================================
    // Convert incoming intent extras into an internal pending nav request.
    // This keeps intent handling decoupled from Compose lifecycle and NavController readiness.
    // =============================================================
    private fun processIntentForNavigation(intent: Intent) {
        try {
            val fromNotification = intent.getBooleanExtra("from_notification", false)
            val reminderId = intent.getLongExtra("reminder_id", -1L)
            val eventType = intent.getStringExtra("event_type")

            Timber.tag(TAG).d("processIntentForNavigation → fromNotification=$fromNotification id=$reminderId type=$eventType")

            if (fromNotification && reminderId != -1L) {
                // Save a pending request — UI will consume it when navController is ready.
                pendingNavRequest.value = PendingNavRequest(reminderId = reminderId, eventType = eventType)
                // Remove extras to avoid double-handling if activity re-creates
                intent.removeExtra("from_notification")
                intent.removeExtra("reminder_id")
                intent.removeExtra("event_type")
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Error while processing intent for navigation")
        }
    }

    // Small data holder for pending navigation
    private data class PendingNavRequest(val reminderId: Long, val eventType: String?)
}
