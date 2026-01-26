package com.example.eventreminder.ui.screens

// =============================================================
// Imports
// =============================================================
import android.os.PowerManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventreminder.logging.AUTH_STATE_TAG
import com.example.eventreminder.ui.viewmodels.SplashViewModel
import com.example.eventreminder.ui.viewmodels.SplashViewModel.AuthGate
import timber.log.Timber

/**
 * SplashScreen
 *
 * Entry point for auth-state resolution.
 * UI-only: reacts to AuthGate emitted by SplashViewModel.
 *
 * Logging rule enforced:
 * [SplashScreen.kt::FunctionName]
 */
@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToEmailVerification: () -> Unit,
    onBatteryFixRequired: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var hasNavigated by remember { mutableStateOf(false) }

    Timber.tag(AUTH_STATE_TAG).d("Splash screen started [SplashScreen.kt::SplashScreen]")

    // ------------------------------------------------------------
    // Battery Optimization Check (once)
    // ------------------------------------------------------------
    val isBatteryOptimized = remember {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) == false
    }

    // ------------------------------------------------------------
    // Observe AuthGate
    // ------------------------------------------------------------
    val authGate by viewModel.authGate.collectAsState()

    // ------------------------------------------------------------
    // ONE-TIME INITIALIZATION
    // ------------------------------------------------------------
    LaunchedEffect(Unit) {
        //Timber.tag(AUTH_STATE_TAG).i("INIT → ViewModel.initialize() [SplashScreen.kt::LaunchedEffect]")
        viewModel.initialize()
    }

    // ------------------------------------------------------------
    // NAVIGATION GATE (EXACTLY ONCE)
    // ------------------------------------------------------------
    LaunchedEffect(authGate) {

        if (hasNavigated || authGate == null) return@LaunchedEffect

        // Highest priority: battery optimization
        if (isBatteryOptimized) {
            Timber.tag(AUTH_STATE_TAG)
                .w("STATE → BATTERY_OPTIMIZED [SplashScreen.kt::LaunchedEffect]")
            hasNavigated = true
            onBatteryFixRequired()
            return@LaunchedEffect
        }

        hasNavigated = true

        when (authGate) {

            AuthGate.LOGGED_OUT -> {
                Timber.tag(AUTH_STATE_TAG)
                    .i("STATE → LOGGED_OUT → Login [SplashScreen.kt::LaunchedEffect]")
                onNavigateToLogin()
            }

            AuthGate.EMAIL_UNVERIFIED -> {
                Timber.tag(AUTH_STATE_TAG)
                    .w("STATE → EMAIL_UNVERIFIED → VerifyEmail [SplashScreen.kt::LaunchedEffect]")
                onNavigateToEmailVerification()
            }

            AuthGate.READY -> {
                Timber.tag(AUTH_STATE_TAG)
                    .i("STATE → READY → Home [SplashScreen.kt::LaunchedEffect]")
                onNavigateToHome()
            }

            null -> Unit
        }
    }

    // ------------------------------------------------------------
    // UI (pure)
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
