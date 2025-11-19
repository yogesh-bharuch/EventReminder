package com.example.eventreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
//import com.example.birthdaywish.data.EventsViewModel
//import com.example.birthdaywish.ui.components.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp

/**
 * 🎉 BirthdayEntryScreen
 *
 * This is a full-screen composable used for creating or editing a birthday/event entry.
 * It interacts directly with [EventsViewModel] for reading and updating input state.
 *
 * - If [eventId] is not null, it means the user is editing an existing entry.
 * - Otherwise, a new event is being created.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEntryScreen(
    navController: NavController,
    //viewModel: EventsViewModel = hiltViewModel(),
    eventId: String? = null
) {
    /*LaunchedEffect(eventId) {
        if (eventId != null) {
            viewModel.loadEventData(eventId)
        }
    }
    // 🧠 Collect state from ViewModel
    val title = viewModel.title
    val description = viewModel.description
    val eventDate = viewModel.eventDate
    val eventType = viewModel.eventType
    val isSaving = viewModel.isSaving*/

    // 🎯 Snackbar for showing save/update messages
    val snackbarHostState = remember { SnackbarHostState() }

    // 🧱 Scaffold provides top bar and layout structure
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (eventId != null) "Edit Event" else "Add Event") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) } // ✅ attach SnackbarHost
    ) { padding ->

        Card(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                /*// 🎯 Event Type
                EventTypeInputField(
                    selectedType = eventType,
                    onTypeSelected = viewModel::onEventSelected,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                // 👤 Title
                NameInputField(
                    name = title,
                    onNameChange = viewModel::onTitleChange,
                    eventType = eventType,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                // 📝 Description
                DescriptionInputField(
                    description = description,
                    onDescriptionChange = viewModel::onDescriptionChange,
                    eventType = eventType,
                    modifier = Modifier.fillMaxWidth()
                )

                EventDatePickerField(
                    selectedDate = eventDate,
                    onDateSelected = viewModel::onDateSelected,
                    eventType = eventType,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))

                //if (viewModel.eventType == EventType.ONE_TIME) {
                Spacer(Modifier.height(8.dp))
                ReminderOptionDropdown(
                    selectedReminder = viewModel.reminderOption,
                    onReminderSelected = viewModel::onReminderOptionSelected,
                    modifier = Modifier.fillMaxWidth()
                )
                //}
                Spacer(Modifier.height(16.dp))

                // ✅ Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Cancel
                    OutlinedButton(
                        onClick = {
                            viewModel.cancelEntry() // 🧹 Reset form state
                            navController.popBackStack()
                        },
                        enabled = !isSaving
                    ) { Text("Cancel") }


                    // Save / Update
                    Button(
                        onClick = {
                            if (eventId != null) {
                                // ✏️ Update existing event
                                viewModel.updateEntry(
                                    id = eventId,
                                    snackbarHostState = snackbarHostState,
                                    onSuccess = { navController.popBackStack() }
                                )
                            } else {
                                // 🆕 Save new event
                                viewModel.saveAndNotify(
                                    snackbarHostState = snackbarHostState,
                                    onSuccess = { navController.popBackStack() }
                                )
                            }
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving)
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        else
                            Icon(Icons.Default.Save, contentDescription = "Save")

                        Spacer(Modifier.width(4.dp))
                        Text(if (eventId != null) "Update" else "Save")
                    }
                }*/
            }
        }
    }
}
