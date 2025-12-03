package com.example.eventreminder.ui.screens


import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.model.ReminderOffset
import com.example.eventreminder.data.model.ReminderTitle
import com.example.eventreminder.data.model.RepeatRule
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import timber.log.Timber
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditReminderScreen(
    navController: NavController,
    eventId: String?,
    reminderVm: ReminderViewModel
) {
    val context = LocalContext.current

    // ViewModel state
    val uiState by reminderVm.uiState.collectAsState()

    // Convert incoming ID
    val reminderId = eventId?.toLongOrNull()
    val zoneId = ZoneId.systemDefault()

    // --------------------------------------------------------------
    // LOCAL COMPOSE STATES FOR INPUTS
    // --------------------------------------------------------------
    var title by remember { mutableStateOf(ReminderTitle.EVENT) }
    var description by remember { mutableStateOf("") }
    var pickedDate by remember { mutableStateOf(LocalDate.now()) }
    var pickedTime by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }

    var selectedOffsets by remember { mutableStateOf(mutableSetOf<ReminderOffset>()) }
    var selectedRepeat by remember { mutableStateOf(RepeatRule.NONE) }

    Timber.tag("ADD_VM").d("VM instance: $reminderVm")

    // --------------------------------------------------------------
    // 1) RESET UI FOR ADD MODE
    // --------------------------------------------------------------
    LaunchedEffect(eventId) {
        if (eventId == null) {
            title = ReminderTitle.EVENT
            description = ""
            pickedDate = LocalDate.now()
            pickedTime = LocalTime.now().withSecond(0).withNano(0)
            selectedOffsets = mutableSetOf()
            selectedRepeat = RepeatRule.NONE
        }
    }

    // --------------------------------------------------------------
    // 2) LOAD REMINDER IN EDIT MODE
    // --------------------------------------------------------------
    LaunchedEffect(reminderId) {
        if (reminderId != null) reminderVm.load(reminderId)
    }

    // --------------------------------------------------------------
    // 3) POPULATE UI ONCE VM LOADS DATA
    // --------------------------------------------------------------
    LaunchedEffect(uiState.editReminder) {
        uiState.editReminder?.let { r ->
            title = ReminderTitle.entries.find { it.label == r.title } ?: ReminderTitle.EVENT
            description = r.description ?: ""

            val zdt = Instant.ofEpochMilli(r.eventEpochMillis)
                .atZone(ZoneId.of(r.timeZone))

            pickedDate = zdt.toLocalDate()
            pickedTime = zdt.toLocalTime()

            selectedOffsets = r.reminderOffsets
                .mapNotNull { ReminderOffset.fromMillis(it) }
                .toMutableSet()

            selectedRepeat = RepeatRule.fromKey(r.repeatRule)

            // Clear VM state so ADD mode won't reuse stale data
            reminderVm.clearEditReminder()
        }
    }

    // --------------------------------------------------------------
    // 4) VALIDATION SNACKBAR (local)
    // --------------------------------------------------------------
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            SnackbarHostState().showSnackbar(it)
            reminderVm.resetError()
        }
    }

    // --------------------------------------------------------------
    // DATE PICKER
    // --------------------------------------------------------------
    val datePicker = remember(title) {
        DatePickerDialog(
            context,
            { _: DatePicker, y, m, d ->
                pickedDate = LocalDate.of(y, m + 1, d)
            },
            pickedDate.year,
            pickedDate.monthValue - 1,
            pickedDate.dayOfMonth
        ).apply {

            val todayMillis = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()

            when (title) {
                ReminderTitle.BIRTHDAY,
                ReminderTitle.ANNIVERSARY -> datePicker.maxDate = todayMillis
                else -> datePicker.minDate = todayMillis
            }
        }
    }

    // --------------------------------------------------------------
    // TIME PICKER
    // --------------------------------------------------------------
    val timePicker = remember {
        TimePickerDialog(
            context,
            { _: TimePicker, h, mi -> pickedTime = LocalTime.of(h, mi) },
            pickedTime.hour,
            pickedTime.minute,
            true
        )
    }

    // --------------------------------------------------------------
    // UI LAYOUT
    // --------------------------------------------------------------
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (reminderId == null) "Add Reminder" else "Edit Reminder")
                }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // TITLE
            item {
                var expanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                    OutlinedTextField(
                        value = title.label,
                        onValueChange = {},
                        label = { Text("Title") },
                        readOnly = true,
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        ReminderTitle.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    title = option
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // DESCRIPTION
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Name / Event details") },
                    leadingIcon = { Icon(Icons.Default.Info, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            // DATE & TIME
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { datePicker.show() }) {
                        Text("Date: $pickedDate")
                    }
                    Button(onClick = { timePicker.show() }) {
                        Text("Time: $pickedTime")
                    }
                }
            }

            // MULTI-OFFSETS
            item {
                Text("Reminder Alerts", style = MaterialTheme.typography.titleMedium)

                ReminderOffset.entries.forEach { off ->
                    Row(Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = off in selectedOffsets,
                            onCheckedChange = { checked ->
                                selectedOffsets = selectedOffsets.toMutableSet().apply {
                                    if (checked) add(off) else remove(off)
                                }
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(off.label)
                    }
                }
            }

            // REPEAT RULE
            item {
                var expanded by remember { mutableStateOf(false) }

                Text("Repeat Rule")

                ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedRepeat.label,
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = { Icon(Icons.Default.Repeat, null) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    ExposedDropdownMenu(expanded, { expanded = false }) {
                        RepeatRule.entries.forEach { rule ->
                            DropdownMenuItem(
                                text = { Text(rule.label) },
                                onClick = {
                                    selectedRepeat = rule
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // SAVE BUTTON
            item {
                Button(
                    onClick = {
                        val zdt = ZonedDateTime.of(pickedDate, pickedTime, zoneId)

                        val reminder = EventReminder(
                            id = reminderId ?: 0L,
                            title = title.label,
                            description = description.ifBlank { null },
                            eventEpochMillis = zdt.toInstant().toEpochMilli(),
                            timeZone = zoneId.id,
                            repeatRule = selectedRepeat.key,
                            reminderOffsets = selectedOffsets.map { it.millis },
                            enabled = true
                        )

                        reminderVm.saveReminder(reminder)

                        // ⭐ Navigation happens immediately.
                        //    Snackbar is handled by HomeScreen via SharedFlow.
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (reminderId == null) "Save" else "Update")
                }
            }
        }
    }
}
