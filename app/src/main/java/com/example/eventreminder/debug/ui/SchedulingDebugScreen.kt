package com.example.eventreminder.debug.ui

// =============================================================
// Sound Test Debug Screen
// Developer-only screen to manually test notification sounds.
// =============================================================

// =============================================================
// Imports
// =============================================================
import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.eventreminder.R
import timber.log.Timber

// =============================================================
// Constants
// =============================================================
private const val TAG = "SoundTestDebugScreen"

// =============================================================
// Screen
// =============================================================
@Composable
fun SchedulingDebugScreen() {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "Sound Test",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Tap a button to play the corresponding sound",
                style = MaterialTheme.typography.bodyMedium
            )

            SoundTestButtons(context = context)
        }
    }
}

// =============================================================
// Sound Test Buttons
// =============================================================
@Composable
private fun SoundTestButtons(
    context: Context
) {

    fun playSound(resId: Int) {
        Timber.tag(TAG).d("Playing sound resId=$resId")

        MediaPlayer.create(context, resId)?.apply {
            setOnCompletionListener {
                Timber.tag(TAG).d("Sound completed resId=$resId")
                release()
            }
            start()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { playSound(R.raw.birthday) }
        ) {
            Text("Play Birthday Sound")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { playSound(R.raw.anniversary) }
        ) {
            Text("Play Anniversary Sound")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { playSound(R.raw.medicine) }
        ) {
            Text("Play Medicine Sound")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { playSound(R.raw.meeting) }
        ) {
            Text("Play Meeting Sound")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { playSound(R.raw.workout) }
        ) {
            Text("Play Workout Sound")
        }
    }
}
