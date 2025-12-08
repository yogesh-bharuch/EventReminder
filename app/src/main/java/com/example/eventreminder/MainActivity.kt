package com.example.eventreminder

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
import com.example.eventreminder.navigation.PixelPreviewRoute
import com.example.eventreminder.ui.theme.EventReminderTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private companion object {
        const val TAG = "NotificationTest"
        const val EXTRA_FROM_NOTIFICATION = "from_notification"
        const val EXTRA_REMINDER_ID_STRING = "reminder_id_string"   // UUID key
        const val EXTRA_EVENT_TYPE = "event_type"
    }

    // Pending navigation request now stores a STRING UUID
    private val pendingNavRequest = mutableStateOf<PendingNavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Process initial intent
        processIntentForNavigation(intent)

        setContent {
            val navController = rememberNavController()

            val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
            val start = if (isLoggedIn) HomeGraphRoute else LoginRoute

            EventReminderTheme {
                AppNavGraph(
                    navController = navController,
                    startDestination = start
                )

                // Consume pending navigation request
                LaunchedEffect(isLoggedIn, navController) {
                    val req = pendingNavRequest.value
                    if (req != null) {
                        if (isLoggedIn) {
                            Timber.tag(TAG).d("Navigating → PixelPreviewRoute(reminderId=${req.reminderId})")
                            navController.navigate(PixelPreviewRoute(reminderId = req.reminderId))
                            pendingNavRequest.value = null
                        } else {
                            Timber.tag(TAG).w("Navigation skipped → user not logged in")
                            pendingNavRequest.value = null
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntentForNavigation(intent)
    }

    /**
     * Reads UUID navigation extras from an intent and stores them until NavController is ready.
     */
    private fun processIntentForNavigation(intent: Intent) {
        try {
            val fromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)
            val idString = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)   // UUID ✔
            val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE)

            Timber.tag(TAG).d(
                "processIntentForNavigation → from=$fromNotification idString=$idString type=$eventType"
            )

            if (fromNotification && !idString.isNullOrBlank()) {
                pendingNavRequest.value = PendingNavRequest(reminderId = idString, eventType = eventType)

                // Clean intent to avoid re-trigger
                intent.removeExtra(EXTRA_FROM_NOTIFICATION)
                intent.removeExtra(EXTRA_REMINDER_ID_STRING)
                intent.removeExtra(EXTRA_EVENT_TYPE)
            }

        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Error processing notification intent")
        }
    }

    /** Stores UUID-based pending navigation request */
    private data class PendingNavRequest(
        val reminderId: String,
        val eventType: String?
    )
}
