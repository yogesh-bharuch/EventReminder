package com.example.eventreminder

// =============================================================
// MainActivity
// - ALWAYS starts at SplashRoute (auth gate)
// - FIXED warm-start navigation using stable Compose state key
// - Handles notification deep links without bypassing login
// - PixelPreview navigation occurs ONLY after NavHost is mounted
// =============================================================

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.rememberNavController
import com.example.eventreminder.navigation.AppNavGraph
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

    // IMPORTANT — stable Compose state used as LaunchedEffect key
    private var pendingReminderId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.tag(TAG).d("onCreate — initial intent = $intent")

        // Extract pending ID for Compose key
        pendingReminderId = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)

        setContent {

            val navController = rememberNavController()
            val context = LocalContext.current

            EventReminderTheme {

                // Always start at SplashRoute (auth gate)
                AppNavGraph(
                    context = context,
                    navController = navController,
                    startDestination = SplashRoute
                )

                // =============================================================
                // FIXED: Notification navigation uses a stable Compose key
                // =============================================================
                LaunchedEffect(key1 = pendingReminderId) {

                    if (pendingReminderId == null) {
                        Timber.tag(TAG).d("LaunchedEffect: No pending reminder → return")
                        return@LaunchedEffect
                    }

                    // Wait for Splash → Home navigation to complete
                    delay(350)

                    val uuid = pendingReminderId
                    val fromNotification =
                        this@MainActivity.intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)

                    Timber.tag(TAG).i(
                        "LaunchedEffect(pendingReminderId=$pendingReminderId) → fromNotification=$fromNotification"
                    )

                    if (fromNotification && !uuid.isNullOrBlank()) {
                        Timber.tag(TAG).i("Navigating to PixelPreview → UUID=$uuid")

                        navController.navigate(
                            PixelPreviewRouteString(reminderIdString = uuid)
                        ) {
                            launchSingleTop = true
                        }
                    }

                    // Avoid duplicate triggers
                    this@MainActivity.intent.removeExtra(EXTRA_FROM_NOTIFICATION)
                    this@MainActivity.intent.removeExtra(EXTRA_REMINDER_ID_STRING)
                    this@MainActivity.intent.removeExtra(EXTRA_EVENT_TYPE)

                    // Clear state key
                    pendingReminderId = null
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        Timber.tag(TAG).d("onNewIntent → $intent")

        // Replace activity intent so extras are updated
        setIntent(intent)

        // Update Compose state key (THIS TRIGGERS LaunchedEffect)
        pendingReminderId = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)?.also {
            Timber.tag(TAG).i("Updated pendingReminderId → $it")
        }
    }
}
