package com.example.eventreminder.ui.components

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import timber.log.Timber

@Composable
fun BatteryOptimizationDialog(
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val manufacturer = Build.MANUFACTURER.lowercase()

    Timber.tag("BatteryDialog").i("Detected OEM: $manufacturer")

    val instructions = getDeviceSpecificInstructions(manufacturer)

    AlertDialog(
        onDismissRequest = { },
        title = {
            Text("Disable Battery Optimization")
        },
        text = {
            Column {
                Text(
                    "To keep EventReminder running properly and to stay logged in, please disable battery optimization.\n"
                )

                Spacer(Modifier.height(6.dp))

                Text(instructions)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    context.startActivity(intent)
                    onContinue()
                }
            ) {
                Text("Open Settings")
            }
        }
    )
}

private fun getDeviceSpecificInstructions(manufacturer: String): String {
    return when {
        manufacturer.contains("oneplus") -> """
            OnePlus detected:

            1. Go to Battery → Battery Optimization
               - Find "EventReminder"
               - Set to "Don't Optimize"

            2. Go to Apps → EventReminder → Battery
               - Enable "Allow Background Activity"

            3. Go to Apps → Auto-launch
               - Enable Auto-launch for EventReminder

            These steps prevent OnePlus from killing Firebase Auth, 
            which causes repeated login prompts.
        """.trimIndent()

        manufacturer.contains("oppo") || manufacturer.contains("realme") -> """
            Oppo / Realme detected:

            1. Settings → Battery → App Battery Management
               - Set EventReminder to "Allow Background Activity"

            2. Settings → Battery Optimization
               - Set EventReminder to "Don't Optimize"

            3. Settings → App Management → Auto-launch
               - Enable Auto-launch

            These steps prevent the device from killing login tokens.
        """.trimIndent()

        manufacturer.contains("vivo") -> """
            Vivo detected:

            1. Settings → Battery → High Background Power Consumption
               - Allow EventReminder

            2. Settings → Battery Optimization
               - Exclude EventReminder

            Vivo’s FuntouchOS is very aggressive; these steps are required.
        """.trimIndent()

        manufacturer.contains("xiaomi") -> """
            Xiaomi (MIUI) detected:

            1. Settings → Apps → Permissions → Autostart
               - Enable Autostart for EventReminder

            2. Settings → Battery → App Battery Saver
               - Set EventReminder to "No Restrictions"

            MIUI may otherwise kill background services and remove logins.
        """.trimIndent()

        else -> """
            Your device may restrict background processes.

            Please:
            1. Disable Battery Optimization for EventReminder
            2. Allow Background Activity (if available)
            3. Enable Auto-start (if available)

            This prevents your login session from being wiped.
        """.trimIndent()
    }
}
