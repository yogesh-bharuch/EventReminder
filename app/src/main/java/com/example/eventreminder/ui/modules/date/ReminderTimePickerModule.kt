package com.example.eventreminder.ui.modules.time

import android.app.TimePickerDialog
import android.widget.TimePicker
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import timber.log.Timber
import java.time.LocalTime

private const val TAG = "ReminderTimePickerModule"

/**
 * ---------------------------------------------------------
 *  ReminderTimePickerModule
 * ---------------------------------------------------------
 *
 * UI-only wrapper around TimePickerDialog.
 *  - Pure Composable
 *  - No business logic
 *  - Fully driven by ViewModel state
 *
 * Returns selected LocalTime to caller.
 */
@Composable
fun ReminderTimePickerModule(
    selectedTime: LocalTime,
    onTimeChanged: (LocalTime) -> Unit
) {
    val context = LocalContext.current

    // Controls when dialog is shown
    val showDialog = remember { mutableStateOf(false) }

    if (showDialog.value) {
        ShowTimePickerDialog(
            initial = selectedTime,
            onDismiss = { showDialog.value = false },
            onSelected = { time ->
                Timber.tag(TAG).d("Time picked: $time")
                onTimeChanged(time)
                showDialog.value = false
            }
        )
    }

    // Simple button UI
    Button(onClick = { showDialog.value = true }) {
        Text("Time: $selectedTime")
    }
}

/**
 * ---------------------------------------------------------
 *  Internal TimePickerDialog handler
 * ---------------------------------------------------------
 */
@Composable
private fun ShowTimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onSelected: (LocalTime) -> Unit
) {
    val context = LocalContext.current

    val dialog = remember(initial) {
        TimePickerDialog(
            context,
            { _: TimePicker, h, m ->
                onSelected(LocalTime.of(h, m))
            },
            initial.hour,
            initial.minute,
            true
        )
    }

    // Ensure dialog closes when composable leaves
    LaunchedEffect(Unit) {
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
    }
}
