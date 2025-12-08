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
import com.example.eventreminder.navigation.PixelPreviewRouteString
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

    // Stores a UUID navigation request until NavController is ready
    private val pendingNavRequest = mutableStateOf<PendingNavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Read initial intent (app launched from notification)
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

                // Perform navigation once UI + NavController are ready
                LaunchedEffect(isLoggedIn, navController) {
                    val req = pendingNavRequest.value

                    if (req != null) {
                        if (isLoggedIn) {
                            Timber.tag(TAG).d(
                                "Launching Pixel Card Editor → UUID=${req.reminderIdString}"
                            )

                            navController.navigate(
                                PixelPreviewRouteString(
                                    reminderIdString = req.reminderIdString
                                )
                            )
                        } else {
                            Timber.tag(TAG).w(
                                "Notification tap ignored — user not logged in"
                            )
                        }

                        pendingNavRequest.value = null
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
     * Extracts UUID navigation extras and saves them to pendingNavRequest.
     */
    private fun processIntentForNavigation(intent: Intent) {
        try {
            val fromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)
            val uuid = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)
            val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE)

            Timber.tag(TAG).d(
                "processIntentForNavigation → fromNotif=$fromNotification uuid=$uuid type=$eventType"
            )

            if (fromNotification && !uuid.isNullOrBlank()) {

                // Store UUID request for Compose to consume later
                pendingNavRequest.value = PendingNavRequest(
                    reminderIdString = uuid,
                    eventType = eventType
                )

                // Prevent re-triggering on configuration changes
                intent.removeExtra(EXTRA_FROM_NOTIFICATION)
                intent.removeExtra(EXTRA_REMINDER_ID_STRING)
                intent.removeExtra(EXTRA_EVENT_TYPE)
            }

        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to parse navigation intent")
        }
    }

    /**
     * UUID-based navigation request holder
     */
    private data class PendingNavRequest(
        val reminderIdString: String,
        val eventType: String?
    )
}
