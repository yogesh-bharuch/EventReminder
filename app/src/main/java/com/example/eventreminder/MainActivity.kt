package com.example.eventreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import timber.log.Timber
import androidx.navigation.compose.rememberNavController
import com.example.eventreminder.navigation.AppNavGraph
import com.example.eventreminder.ui.theme.EventReminderTheme // 👈 Import your theme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import com.example.eventreminder.navigation.HomeRoute
import com.example.eventreminder.navigation.LoginRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val start = if (FirebaseAuth.getInstance().currentUser != null)
            HomeRoute else LoginRoute

        setContent {
            val navController = rememberNavController()
            Timber.d("🪵 App started")

            // 🎨 Apply your custom Material 3 theme
            EventReminderTheme {
                AppNavGraph(
                    navController = navController,
                    startDestination = start
                )
            }
        }
    }
}