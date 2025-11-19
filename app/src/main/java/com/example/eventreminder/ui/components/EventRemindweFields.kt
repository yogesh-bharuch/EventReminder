package com.example.eventreminder.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.eventreminder.ui.viewmodels.EventReminderUI
import com.example.eventreminder.ui.viewmodels.GroupedUiSection
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter


// -----------------------------------------------------------------------------
// 🏠 HOME SCAFFOLD — Restored Original Version (with comments)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScaffold(
    onNewEventClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onSignOut: () -> Unit,
    onManageRemindersClick: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(

        // 🟦 Top App Bar
        topBar = {
            TopAppBar(
                title = { Text("Events") },
                actions = {

                    // ⚙️ Manage Reminders Button
                    IconButton(onClick = onManageRemindersClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage Reminders")
                    }

                    // 🚪 Sign Out Button
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

        // ➕ Floating Action Button
        floatingActionButton = {
            FloatingActionButton(onClick = onNewEventClick) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->

        // Content slot
        content(
            Modifier
                .padding(padding)
                .padding(8.dp)
        )
    }
}



// -----------------------------------------------------------------------------
// 📭 EMPTY STATE — Restored Original Version (with comments)
// -----------------------------------------------------------------------------
@Composable
fun BirthdayEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "No reminders yet. Tap + to add one.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// -----------------------------------------------------------------------------
// 🗂 SORT + SWIPE DELETE EVENTS LIST (Updated + Fixed)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsListGrouped(
    sections: List<GroupedUiSection>,
    onClick: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutine = rememberCoroutineScope()

    // Track which groups are collapsed
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }
    sections.forEach { section ->
        if (!collapsed.containsKey(section.header))
            collapsed[section.header] = false  // default expanded
    }

    Box(Modifier.fillMaxSize()) {

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            sections.forEach { section ->

                // ⭐ STICKY HEADER
                stickyHeader {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                collapsed[section.header] =
                                    !(collapsed[section.header]!!)
                            }
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp, horizontal = 10.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(section.header, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                                Text(if (collapsed[section.header] == true) "+" else "–", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider()
                        }
                    }
                }

                // ⭐ COLLAPSIBLE CONTENT
                item {
                    AnimatedVisibility(
                        visible = collapsed[section.header] == false,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                            section.events.forEach { ui ->

                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart ||
                                            value == SwipeToDismissBoxValue.StartToEnd
                                        ) {

                                            coroutine.launch {
                                                onDelete(ui.id)

                                                val result = snackbarHostState.showSnackbar(message = "Event deleted", actionLabel = "Undo")

                                                if (result == SnackbarResult.ActionPerformed) {
                                                    // TODO undo if needed
                                                }
                                            }

                                            true
                                        }
                                        false
                                    }
                                )

                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {
                                        SwipeBackgroundM3(dismissState)
                                    },
                                    content = {
                                        EventCard(
                                            ui = ui,
                                            onClick = { onClick(ui.id) },
                                            onDelete = {
                                                coroutine.launch {
                                                    onDelete(ui.id)
                                                    snackbarHostState.showSnackbar("Event deleted")
                                                }
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
        )
    }
}



// -----------------------------------------------------------------------------
// 🟥 SWIPE DELETE BACKGROUND (Material3)
// -----------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeBackgroundM3(state: SwipeToDismissBoxState) {
    val color = when (state.targetValue) {
        SwipeToDismissBoxValue.StartToEnd,
        SwipeToDismissBoxValue.EndToStart ->
            Color.Red.copy(alpha = 0.85f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(20.dp),
        contentAlignment =
            if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = "Delete",
            tint = Color.White
        )
    }
}



// -----------------------------------------------------------------------------
// 🎴 EVENT CARD
// -----------------------------------------------------------------------------
@Composable
fun EventCard(
    ui: EventReminderUI,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val repeatLabel = when (ui.repeatRule) {
        "every_minute" -> "Every Minute"
        "daily" -> "Daily"
        "weekly" -> "Weekly"
        "monthly" -> "Monthly"
        "yearly" -> "Yearly"
        else -> null   // one-time → no chip
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            // LEFT CONTENT
            Column(modifier = Modifier.weight(1f)) {

                // 🔹 TITLE and description if not null
                Text(text = ui.title, style = MaterialTheme.typography.titleSmall)
                ui.description?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }

                Spacer(modifier = Modifier.height(2.dp))

                // 🔹 DATE (line 1)
                Text(
                    text = ui.formattedDateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                // 🔹 REPEAT + REMAINING (line 2)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeatLabel?.let {
                        AssistChip(
                            onClick = {},
                            label = { Text(it) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }

                    Text(
                        text = "⏳ ${ui.timeRemainingLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 🔹 DELETE BUTTON
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}


// -----------------------------------------------------------------------------
// ⏳ TIME REMAINING FORMATTER
// -----------------------------------------------------------------------------
fun formatTimeRemaining(eventEpoch: Long): String {
    val now = Instant.now()
    val eventInstant = Instant.ofEpochMilli(eventEpoch)
    val duration = Duration.between(now, eventInstant)

    val seconds = duration.seconds

    return when {
        seconds < 0 -> "Ellapsed"
        seconds < 60 -> "In a few seconds"
        seconds < 3600 -> "In ${seconds / 60} minutes"
        seconds < 86_400 -> "In ${seconds / 3600} hours"
        seconds < 172_800 -> "Tomorrow"
        seconds < 604_800 -> "In ${seconds / 86_400} days"
        else -> {
            val date = eventInstant.atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
            "On $date"
        }
    }
}
