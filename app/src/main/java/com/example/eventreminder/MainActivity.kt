package com.example.eventreminder

// =============================================================
// MainActivity (Final Clean Version)
// - Always starts at SplashRoute (auth gate)
// - Handles notification deep links (cold + warm start)
// - Uses stable Compose key for safe navigation
// - Minimal, clean debug logs only
// =============================================================

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.example.eventreminder.navigation.AppNavGraph
import com.example.eventreminder.navigation.HomeGraphRoute
import com.example.eventreminder.navigation.PixelPreviewRouteString
import com.example.eventreminder.navigation.SplashRoute
import com.example.eventreminder.ui.theme.EventReminderTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import timber.log.Timber

private const val TAG = "MainActivity"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private companion object {
        const val EXTRA_FROM_NOTIFICATION = "from_notification"
        const val EXTRA_REMINDER_ID_STRING = "reminder_id_string"
        const val EXTRA_EVENT_TYPE = "event_type"
    }

    // Stable state key for LaunchedEffect
    private var pendingReminderId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Extract pending reminder (if launched from notification)
        pendingReminderId = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)
        Timber.tag(TAG).d("onCreate → pendingReminderId=$pendingReminderId")

        setContent {

            val navController = rememberNavController()
            val context = LocalContext.current

            EventReminderTheme {

                // Always start at SplashRoute — decides Login vs Home
                AppNavGraph(
                    context = context,
                    navController = navController,
                    startDestination = SplashRoute
                )

                // ---------------------------------------------------------
                // Notification → PixelPreview Navigation Handler
                // ---------------------------------------------------------
                LaunchedEffect(pendingReminderId) {

                    val uuid = pendingReminderId
                    if (uuid == null) return@LaunchedEffect

                    // Allow Splash → Home navigation to settle
                    delay(350)

                    val fromNotification =
                        intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)

                    if (fromNotification && uuid.isNotBlank()) {

                        Timber.tag(TAG).d("Navigate PixelPreview → UUID=$uuid")

                        // Step 1 — Ensure HomeGraph is active
                        navController.navigate(HomeGraphRoute) {
                            launchSingleTop = true
                            restoreState = true
                        }

                        delay(120) // Let HomeGraph mount

                        // Step 2 — Navigate to PixelPreview inside HomeGraph
                        navController.navigate(
                            PixelPreviewRouteString(reminderIdString = uuid)
                        ) {
                            launchSingleTop = true
                        }
                    }

                    // Prevent multiple triggers
                    intent.removeExtra(EXTRA_REMINDER_ID_STRING)
                    intent.removeExtra(EXTRA_FROM_NOTIFICATION)
                    intent.removeExtra(EXTRA_EVENT_TYPE)

                    pendingReminderId = null
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // Replace Activity intent (required for new notification taps)
        setIntent(intent)

        pendingReminderId = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)
        Timber.tag(TAG).d("onNewIntent → pendingReminderId=$pendingReminderId")
    }
}
