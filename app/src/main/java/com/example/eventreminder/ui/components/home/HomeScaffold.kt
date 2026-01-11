package com.example.eventreminder.ui.components.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
        // ---------------------------------------------------------
        // System window insets (status bar + navigation bar safe)
        // ---------------------------------------------------------
        contentWindowInsets = WindowInsets.safeDrawing,

        topBar = {
            TopAppBar(
                // ✅ Ensure TopAppBar stays below status bar
                windowInsets = TopAppBarDefaults.windowInsets,

                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Event,   // ⭐ Material3 Calendar/Event icon
                            contentDescription = "Events Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = "Events", color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    IconButton(onClick = onManageRemindersClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Manage Reminders"
                        )
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Sign Out"
                        )
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
        bottomBar = {
            // ✅ Ensure bottom tray stays above navigation bar
            Row(
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                bottomBar()
            }
        }
    ) { padding ->

        content(
            Modifier
                .padding(padding)
                .padding(8.dp)
        )
    }
}
