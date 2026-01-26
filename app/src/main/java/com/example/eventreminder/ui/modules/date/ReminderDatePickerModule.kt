
package com.example.eventreminder.ui.modules.date


import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.eventreminder.data.model.ReminderTitle
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAG = "ReminderDatePickerModule"

/**
 * ---------------------------------------------------------
 *  ReminderDatePickerModule
 * ---------------------------------------------------------
 *
 * UI-only wrapper around DatePickerDialog.
 *  - No validation/business logic
 *  - Fully driven by ViewModel state
 *  - Returns pure LocalDate to caller
 *
 * Used from AddEditReminderScreen by simply passing:
 *   selectedDate = pickedDate
 *   title = selected ReminderTitle
 *   onDateChanged = { pickedDate = it }
 */
@Composable
fun ReminderDatePickerModule(
    selectedDate: LocalDate,
    title: ReminderTitle,
    onDateChanged: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ─────────────────────────────────────────────────────────────
    // SHOW DATE PICKER WHEN BTN PRESSED
    // ─────────────────────────────────────────────────────────────
    val showDialog = remember { mutableStateOf(false) }

    // Formatter for display: dd-MM-yyyy
    val displayFormatter = remember {
        DateTimeFormatter.ofPattern("dd-MM-yyyy")
    }

    if (showDialog.value) {
        ShowDatePickerDialog(
            title = title,
            initialDate = selectedDate,
            onDismiss = { showDialog.value = false },
            onSelected = { newDate ->
                Timber.tag(TAG).d("Date picked: $newDate")
                onDateChanged(newDate)
                showDialog.value = false
            }
        )
    }

    // ─────────────────────────────────────────────────────────────
    // BUTTON UI
    // ─────────────────────────────────────────────────────────────
    Button(
        onClick = { showDialog.value = true },
        modifier = modifier   // ⭐ Enable parent to apply focusRequester
    ) {
        Text("Date: ${selectedDate.format(displayFormatter)}")
    }
}

/**
 * ---------------------------------------------------------
 *  Internal DatePickerDialog Handler
 * ---------------------------------------------------------
 */
@Composable
private fun ShowDatePickerDialog(
    title: ReminderTitle,
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onSelected: (LocalDate) -> Unit
) {
    val context = LocalContext.current

    val dialog = remember(title, initialDate) {
        DatePickerDialog(
            context,
            { _: DatePicker, y, m, d ->
                onSelected(LocalDate.of(y, m + 1, d))
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        ).apply {

            val todayEpoch = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()

            when (title) {
                ReminderTitle.BIRTHDAY,
                ReminderTitle.ANNIVERSARY ->
                    this.datePicker.maxDate = todayEpoch

                else ->
                    this.datePicker.minDate = todayEpoch
            }
        }
    }

    LaunchedEffect(Unit) {
        dialog.setOnDismissListener { onDismiss() }
        dialog.show()
    }
}
