package com.example.eventreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.eventreminder.navigation.*
import com.example.eventreminder.ui.theme.EventReminderTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import androidx.compose.runtime.LaunchedEffect

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ---------------------------------------------
        // Step 1: Read notification extras
        // ---------------------------------------------
        val fromNotification = intent.getBooleanExtra("from_notification", false)
        val reminderId = intent.getLongExtra("reminder_id", -1L)
        val eventType  = intent.getStringExtra("event_type")

        // Determine if user is logged in
        val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
        val start = if (isLoggedIn) HomeRoute else LoginRoute

        setContent {

            val navController = rememberNavController()

            Timber.tag("NotificationTest").d(
                "Launch → fromNotification=$fromNotification id=$reminderId type=$eventType"
            )

            EventReminderTheme {

                // Normal App Navigation
                AppNavGraph(
                    navController = navController,
                    startDestination = start
                )

                // ---------------------------------------------
                // Step 2: Safe navigation (only if logged IN)
                // ---------------------------------------------
                LaunchedEffect(isLoggedIn) {

                    if (fromNotification && isLoggedIn) {

                        Timber.tag("MainActivity").d("Handling notification → navigating to CardScreen(reminderId=$reminderId)")

                        // Navigate to FINAL card screen (NOT debug)
                        navController.navigate(
                            CardRoute(reminderId = reminderId)
                        )

                        // Clear extras to avoid re-navigation on configuration change
                        intent.removeExtra("from_notification")
                        intent.removeExtra("reminder_id")
                        intent.removeExtra("event_type")
                    }

                    if (fromNotification && !isLoggedIn) {
                        Timber.tag("MainActivity")
                            .w("User logged out → ignoring notification navigation (correct)")
                    }
                }
            }
        }
    }
}
