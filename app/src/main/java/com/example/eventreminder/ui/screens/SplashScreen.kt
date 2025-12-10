package com.example.eventreminder.ui.screens

import android.os.PowerManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.eventreminder.util.SessionPrefs
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,        // navigate to Home
    onNavigateToLogin: () -> Unit,       // navigate to Login
    onBatteryFixRequired: () -> Unit     // show dialog
) {
    val context = LocalContext.current
    var hasNavigated by remember { mutableStateOf(false) }

    // -------- Correct Battery Optimization Check --------
    val isBatteryOptimized = remember {
        val pm = context.getSystemService(PowerManager::class.java)
        // TRUE means battery optimization is ON (bad)
        pm?.isIgnoringBatteryOptimizations(context.packageName) == false
    }

    LaunchedEffect(Unit) {

        delay(300) // allow FirebaseAuth to initialize properly

        val firebaseUser = FirebaseAuth.getInstance().currentUser
        val prefsUid = SessionPrefs.getUid(context)

        Timber.tag("AUTH_CHECK")
            .e("Initial FirebaseAuth user = $firebaseUser, prefsUid = $prefsUid")

        // -------------------- Battery Optimization --------------------
        if (isBatteryOptimized) {
            Timber.tag("BATTERY").w("Device is optimizing battery → blocking navigation")
            onBatteryFixRequired()
            return@LaunchedEffect
        }

        // -------------------- FirebaseAuth Stabilization --------------------
        var user = firebaseUser
        var retries = 0

        while (user == null && retries < 3) {
            delay(150)
            retries++
            user = FirebaseAuth.getInstance().currentUser
            Timber.tag("AUTH_CHECK")
                .e("Retry($retries): FirebaseAuth user = $user")
        }

        // -------------------- Final Decision --------------------
        val loggedIn = (user != null) || (prefsUid != null)
        Timber.tag("AUTH_CHECK")
            .e("Final decision → loggedIn=$loggedIn")

        // -------------------- Navigate Exactly Once --------------------
        if (!hasNavigated) {
            hasNavigated = true
            if (loggedIn) {
                onNavigateToHome()
            } else {
                onNavigateToLogin()
            }
        }
    }

    // ------------------------ UI ------------------------
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