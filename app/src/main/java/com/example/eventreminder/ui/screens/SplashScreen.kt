package com.example.eventreminder.ui.screens

import android.os.PowerManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
fun SplashScreen(
    onNavigate: (Boolean) -> Unit,       // true = logged in
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

        Timber.tag("AUTH_CHECK")
            .e("Initial FirebaseAuth user = ${FirebaseAuth.getInstance().currentUser}")

        // -------------------- Battery Optimization --------------------
        if (isBatteryOptimized) {
            Timber.tag("BATTERY").w("Device is optimizing battery → blocking navigation")
            onBatteryFixRequired()
            return@LaunchedEffect
        }

        // -------------------- FirebaseAuth Stabilization --------------------
        // On OnePlus, FirebaseAuth may return null for the first 200–500ms.
        var user = FirebaseAuth.getInstance().currentUser
        var retries = 0

        while (user == null && retries < 3) {
            delay(150)
            retries++
            user = FirebaseAuth.getInstance().currentUser
            Timber.tag("AUTH_CHECK")
                .e("Retry($retries): FirebaseAuth user = $user")
        }

        val loggedIn = user != null
        Timber.tag("AUTH_CHECK")
            .e("Final decision → loggedIn=$loggedIn")

        // -------------------- Navigate Exactly Once --------------------
        if (!hasNavigated) {
            hasNavigated = true
            onNavigate(loggedIn)
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
