package com.example.eventreminder.ui.screens

/*// =============================================================
// SplashScreen — FirebaseAuth Is the ONLY Source of Truth
//
// Responsibilities:
// - One-time app initialization hook
// - Normalize DB illegal states ("" → NULL)
// - Handle OEM-delayed Firebase initialization
// - Gate navigation (Home / Login)
// - Never bypass auth
// =============================================================*/

import android.os.PowerManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventreminder.ui.viewmodels.SplashViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import timber.log.Timber

private const val TAG = "SplashScreen"

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onBatteryFixRequired: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var hasNavigated by remember { mutableStateOf(false) }

    Timber.tag(TAG).d("Splash composed (hasNavigated=$hasNavigated)")

    // ------------------------------------------------------------
    // Battery Optimization Check (once)
    // ------------------------------------------------------------
    val isBatteryOptimized = remember {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) == false
    }

    // ------------------------------------------------------------
    // ONE-TIME EFFECT
    // ------------------------------------------------------------
    LaunchedEffect(Unit) {

        Timber.tag(TAG).i("Splash started → init sequence")

        // =========================================================
        // 🔑 DB NORMALIZATION (CRITICAL FIX)
        // =========================================================
        try {
            viewModel.normalizeDatabase()
            Timber.tag(TAG).i("DB normalization completed")
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "DB normalization failed")
        }

        // --------------------------------------------------------
        // OEM FirebaseAuth hydration delay (OnePlus / Samsung etc)
        // --------------------------------------------------------
        delay(300)

        // --------------------------------------------------------
        // Battery Optimization Warning
        // --------------------------------------------------------
        if (isBatteryOptimized) {
            Timber.tag(TAG).w("Battery optimization detected")
            onBatteryFixRequired()
            return@LaunchedEffect
        }

        // --------------------------------------------------------
        // FirebaseAuth readiness retry loop
        // --------------------------------------------------------
        var user = FirebaseAuth.getInstance().currentUser
        var retries = 0

        while (user == null && retries < 20) {
            delay(150)
            retries++
            user = FirebaseAuth.getInstance().currentUser
            Timber.tag(TAG).d("Retry($retries): Firebase user = $user")
        }

        val isLoggedIn = user != null
        Timber.tag(TAG).i("Auth resolved → loggedIn=$isLoggedIn")

        // --------------------------------------------------------
        // Navigate EXACTLY ONCE
        // --------------------------------------------------------
        if (!hasNavigated) {
            hasNavigated = true
            if (isLoggedIn) {
                Timber.tag(TAG).i("Splash → Home")
                onNavigateToHome()
            } else {
                Timber.tag(TAG).i("Splash → Login")
                onNavigateToLogin()
            }
        }
    }

    // ------------------------------------------------------------
    // UI (pure, no logic)
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
