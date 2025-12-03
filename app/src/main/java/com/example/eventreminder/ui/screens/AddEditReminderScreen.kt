package com.example.eventreminder.ui.screens


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.eventreminder.data.model.ReminderOffset
import com.example.eventreminder.data.model.ReminderTitle
import com.example.eventreminder.data.model.RepeatRule
import com.example.eventreminder.ui.modules.actions.ReminderSaveButtonModule
import com.example.eventreminder.ui.modules.date.ReminderDatePickerModule
import com.example.eventreminder.ui.modules.dropdown.ReminderRepeatRuleDropdownModule
import com.example.eventreminder.ui.modules.dropdown.ReminderTitleDropdownModule
import com.example.eventreminder.ui.modules.offsets.ReminderOffsetsModule
import com.example.eventreminder.ui.modules.time.ReminderTimePickerModule
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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
                ReminderTitleDropdownModule(
                    selectedTitle = title,
                    onTitleChanged = { title = it }
                )
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
                    ReminderDatePickerModule(
                        selectedDate = pickedDate,
                        title = title,
                        onDateChanged = { pickedDate = it }
                    )

                    ReminderTimePickerModule(
                        selectedTime = pickedTime,
                        onTimeChanged = { pickedTime = it }
                    )

                }
            }

            // MULTI-OFFSETS
            item {
                ReminderOffsetsModule(
                    selectedOffsets = selectedOffsets,
                    onOffsetsChanged = { updated ->
                        selectedOffsets = updated.toMutableSet()
                    }
                )
            }


            // REPEAT RULE
            item {
                ReminderRepeatRuleDropdownModule(
                    selectedRule = selectedRepeat,
                    onRuleChanged = { selectedRepeat = it }
                )
            }

            // SAVE BUTTON
            item {
                ReminderSaveButtonModule(
                    isEditMode = reminderId != null,
                    modifier = Modifier.fillMaxWidth(),
                    onSave = {
                        reminderVm.onSaveClicked(
                            title = title,
                            description = description,
                            date = pickedDate,
                            time = pickedTime,
                            offsets = selectedOffsets,
                            repeatRule = selectedRepeat,
                            existingId = reminderId
                        )
                        navController.popBackStack()
                    }
                )
            }

        }
    }
}
