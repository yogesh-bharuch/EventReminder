package com.example.eventreminder.ui.screens

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.eventreminder.ui.modules.actions.ReminderSaveButtonModule
import com.example.eventreminder.ui.modules.date.ReminderDatePickerModule
import com.example.eventreminder.ui.modules.dropdown.ReminderRepeatRuleDropdownModule
import com.example.eventreminder.ui.modules.dropdown.ReminderTitleDropdownModule
import com.example.eventreminder.ui.modules.offsets.ReminderOffsetsModule
import com.example.eventreminder.ui.modules.time.ReminderTimePickerModule
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton


// =============================================================
// Add / Edit Reminder Screen
// =============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditReminderScreen(
    navController: NavController,
    eventId: String?,
    reminderVm: ReminderViewModel
) {
    val focusManager = LocalFocusManager.current

    // FocusRequesters for full navigation chain
    val dateFocus = remember { FocusRequester() }
    val timeFocus = remember { FocusRequester() }
    val offsetFocus = remember { FocusRequester() }
    val repeatFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }

    // Auto-open time dialog after date
    val timePickerAutoOpen = remember { mutableStateOf(false) }

    val uiState by reminderVm.uiState.collectAsState()
    val reminderId = eventId?.toLongOrNull()

    // Load reminder
    LaunchedEffect(reminderId) {
        if (reminderId == null) {
            // ⭐ ADD MODE → reset form to blank
            reminderVm.resetAddEditForm()
        } else {
            // ⭐ EDIT MODE → load existing reminder
            reminderVm.load(reminderId)
        }
    }

    // Validation snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            SnackbarHostState().showSnackbar(it)
            reminderVm.resetError()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),   // ⭐ Auto-adjust UI when keyboard opens
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {

                        val icon = if (reminderId == null) Icons.Filled.AddCircle else Icons.Filled.Edit

                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (reminderId == null) "Add Reminder" else "Edit Reminder")
                    }
                }
            )
        }


    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .imePadding(),
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            focusManager.clearFocus()
                            dateFocus.requestFocus()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            // DATE + TIME (stacked vertically)
            item {

                // DATE PICKER BUTTON
                ReminderDatePickerModule(
                    selectedDate = uiState.date,
                    title = uiState.title,
                    onDateChanged = {
                        reminderVm.onDateChanged(it)

                        // Auto-open → Time picker
                        timeFocus.requestFocus()
                        timePickerAutoOpen.value = true
                    },
                    modifier = Modifier.focusRequester(dateFocus)
                )

                Spacer(Modifier.height(12.dp))

                // TIME PICKER BUTTON
                ReminderTimePickerModule(
                    selectedTime = uiState.time,
                    onTimeChanged = { time ->
                        reminderVm.onTimeChanged(time)

                        // Next → Offsets
                        focusManager.clearFocus()
                        offsetFocus.requestFocus()
                    },
                    autoOpen = timePickerAutoOpen.value,
                    modifier = Modifier.focusRequester(timeFocus)
                )
            }

            // OFFSETS
            item {
                ReminderOffsetsModule(
                    selectedOffsets = uiState.offsets,
                    onOffsetsChanged = reminderVm::onOffsetsChanged,
                    modifier = Modifier.focusRequester(offsetFocus)
                )

                // When offsets updated → move to Repeat rule
                LaunchedEffect(uiState.offsets) {
                    repeatFocus.requestFocus()
                }
            }

            // REPEAT RULE
            item {
                ReminderRepeatRuleDropdownModule(
                    selectedRule = uiState.repeat,
                    onRuleChanged = reminderVm::onRepeatChanged,
                    modifier = Modifier.focusRequester(repeatFocus)
                )

                // Auto-move to Save button
                LaunchedEffect(uiState.repeat) {
                    saveFocus.requestFocus()
                }
            }

            // SAVE BUTTON
            item {
                ReminderSaveButtonModule(
                    isEditMode = reminderId != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(saveFocus),
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
