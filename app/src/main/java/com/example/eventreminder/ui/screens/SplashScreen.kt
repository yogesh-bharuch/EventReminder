package com.example.eventreminder.ui.screens

// =============================================================
// SplashScreen — FirebaseAuth Is the ONLY Source of Truth
// - Handles OEM-delayed Firebase initialization (OnePlus/Samsung/ColorOS)
// - Never opens PixelPreview here (auth gate only)
// - Allows HomeScreen to consume pending notification request AFTER auth
// - Navigates Home/Login exactly once
// =============================================================

import android.os.PowerManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import timber.log.Timber

private const val TAG = "SplashScreen"

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,        // Navigate to Home graph
    onNavigateToLogin: () -> Unit,       // Navigate to Login graph
    onBatteryFixRequired: () -> Unit     // Show battery optimization dialog
) {
    val context = LocalContext.current
    var hasNavigated by remember { mutableStateOf(false) }

    // ➜ LOG HERE: detect if Splash recomposes
    Timber.tag(TAG).e("⚠️ SplashScreen COMPOSED / RECOMPOSED (hasNavigated=$hasNavigated)")


    // ------------------------------------------------------------
    // Battery Optimization Check
    // ------------------------------------------------------------
    val isBatteryOptimized = remember {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) == false
    }

    LaunchedEffect(Unit) {

        Timber.tag(TAG).d("Splash started → waiting for Firebase to initialize")

        // OEMs like OnePlus delay FirebaseAuth hydration
        delay(300)

        // ------------------------------------------------------------
        // Get initial auth state
        // ------------------------------------------------------------
        var user = FirebaseAuth.getInstance().currentUser
        Timber.tag(TAG).d("Initial FirebaseAuth user = $user")

        // ------------------------------------------------------------
        // Battery Optimization Warning
        // ------------------------------------------------------------
        if (isBatteryOptimized) {
            Timber.tag(TAG).w("Battery optimization detected → showing dialog")
            onBatteryFixRequired()
            return@LaunchedEffect
        }

        // ------------------------------------------------------------
        // Retry Loop for Slow FirebaseAuth Initialization
        // Some OEMs need up to 2–3 seconds
        // ------------------------------------------------------------
        var retries = 0
        while (user == null && retries < 20) {
            delay(150)
            retries++
            user = FirebaseAuth.getInstance().currentUser
            Timber.tag(TAG).d("Retry($retries): FirebaseAuth user = $user")
        }

        val isLoggedIn = user != null
        Timber.tag(TAG).i("Final isLoggedIn = $isLoggedIn")

        // ------------------------------------------------------------
        // NOTE:
        // *SplashScreen NEVER handles PixelPreview navigation directly*
        // Why?
        // 1. Must not bypass login if logged out
        // 2. Must let HomeScreen open PixelPreview only AFTER HomeGraph loaded
        //
        // MainActivity keeps the intent, and HomeScreen (or HomeGraph VM)
        // will read Activity.intent and decide to open PixelPreview safely.
        // ------------------------------------------------------------

        // ------------------------------------------------------------
        // Navigate Exactly Once
        // ------------------------------------------------------------
        if (!hasNavigated) {
            hasNavigated = true

            if (isLoggedIn) {
                Timber.tag(TAG).i("Splash → HOME")
                onNavigateToHome()
            } else {
                Timber.tag(TAG).i("Splash → LOGIN")
                onNavigateToLogin()
            }
        }
    }

    // ------------------------------------------------------------
    // UI — Simple centered logo/text
    // ------------------------------------------------------------
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "EventReminder",
            style = MaterialTheme.typography.headlineLarge
        )
    }
}
