package com.example.eventreminder.ui.components.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScaffold(
    onNewEventClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onSignOut: () -> Unit,
    onManageRemindersClick: () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Events") },
                actions = {
                    IconButton(onClick = onManageRemindersClick) {
                        Icon(Icons.Default.Settings, "Manage Reminders")
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Sign Out")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewEventClick) {
                Icon(Icons.Default.Add, "Add")
            }
        },
        bottomBar = { bottomBar() }
    ) { padding ->

        content(
            Modifier
                .padding(padding)
                .padding(8.dp)
        )
    }
}
