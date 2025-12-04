package com.example.eventreminder.ui.screens


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.eventreminder.ui.modules.actions.ReminderSaveButtonModule
import com.example.eventreminder.ui.modules.date.ReminderDatePickerModule
import com.example.eventreminder.ui.modules.dropdown.ReminderRepeatRuleDropdownModule
import com.example.eventreminder.ui.modules.dropdown.ReminderTitleDropdownModule
import com.example.eventreminder.ui.modules.offsets.ReminderOffsetsModule
import com.example.eventreminder.ui.modules.time.ReminderTimePickerModule
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import timber.log.Timber
import java.time.ZoneId


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditReminderScreen(
    navController: NavController,
    eventId: String?,
    reminderVm: ReminderViewModel
) {
    val context = LocalContext.current

    // VM-driven state
    val uiState by reminderVm.uiState.collectAsState()

    // Converted edit id
    val reminderId = eventId?.toLongOrNull()
    val zoneId = ZoneId.systemDefault()

    Timber.tag("ADD_VM").d("VM instance: $reminderVm")

    // --------------------------------------------------------------
    // 1) LOAD REMINDER IN EDIT MODE
    // --------------------------------------------------------------
    LaunchedEffect(reminderId) {
        if (reminderId != null) {
            reminderVm.load(reminderId)
        }
    }

    // --------------------------------------------------------------
    // 2) VALIDATION SNACKBAR (local)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {

                        val icon = if (reminderId == null)
                            Icons.Default.AddCircle   // ➕ Add Reminder
                        else
                            Icons.Default.Edit        // ✏️ Edit Reminder

                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = if (reminderId == null) "Add Reminder" else "Edit Reminder"
                        )
                    }
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
                    selectedTitle = uiState.title,
                    onTitleChanged = reminderVm::onTitleChanged
                )
            }

            // DESCRIPTION
            item {
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = reminderVm::onDescriptionChanged,
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
                        selectedDate = uiState.date,
                        title = uiState.title,
                        onDateChanged = reminderVm::onDateChanged
                    )

                    ReminderTimePickerModule(
                        selectedTime = uiState.time,
                        onTimeChanged = reminderVm::onTimeChanged
                    )
                }
            }

            // MULTI-OFFSETS
            item {
                ReminderOffsetsModule(
                    selectedOffsets = uiState.offsets,
                    onOffsetsChanged = reminderVm::onOffsetsChanged
                )
            }

            // REPEAT RULE
            item {
                ReminderRepeatRuleDropdownModule(
                    selectedRule = uiState.repeat,
                    onRuleChanged = reminderVm::onRepeatChanged
                )
            }

            // SAVE BUTTON
            item {
                ReminderSaveButtonModule(
                    isEditMode = reminderId != null,
                    modifier = Modifier.fillMaxWidth(),
                    onSave = {
                        reminderVm.onSaveClicked(
                            title = uiState.title,
                            description = uiState.description,
                            date = uiState.date,
                            time = uiState.time,
                            offsets = uiState.offsets,
                            repeatRule = uiState.repeat,
                            existingId = reminderId
                        )
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
