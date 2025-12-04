package com.example.eventreminder.ui.modules.time

// =============================================================
// Imports
// =============================================================
import android.app.TimePickerDialog
import android.widget.TimePicker
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import timber.log.Timber
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val TAG = "ReminderTimePickerModule"

/**
 * ---------------------------------------------------------
 * ReminderTimePickerModule
 * ---------------------------------------------------------
 *
 * UI-only wrapper around TimePickerDialog.
 * - No business logic inside
 * - ViewModel drives actual state
 * - Supports auto-open when coming from date selection
 *
 * @param selectedTime    Current time from VM
 * @param onTimeChanged   Callback to update VM
 * @param autoOpen        If true → dialog opens automatically
 * @param modifier        Used for focus navigation
 */
@Composable
fun ReminderTimePickerModule(
    selectedTime: LocalTime,
    onTimeChanged: (LocalTime) -> Unit,
    autoOpen: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Control dialog visibility
    val showDialog = remember { mutableStateOf(false) }

    // Auto-open support (e.g., after picking date)
    LaunchedEffect(autoOpen) {
        if (autoOpen) {
            Timber.tag(TAG).d("Auto-opening time picker")
            showDialog.value = true
        }
    }

    // 🔔 If dialog should show → render it
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

    // Display time in "hh:mm a" format
    val formatted = remember(selectedTime) {
        val formatter = DateTimeFormatter.ofPattern("hh:mm a")
        selectedTime.format(formatter)
    }

    // UI Button
    Button(
        modifier = modifier,
        onClick = { showDialog.value = true }
    ) {
        Text("Time: $formatted")
    }
}

/**
 * ---------------------------------------------------------
 * Internal composable that controls TimePickerDialog
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
            false // 12-hour AM/PM format
        )
    }

    LaunchedEffect(Unit) {
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
    }
}
