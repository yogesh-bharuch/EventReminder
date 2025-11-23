package com.example.eventreminder.ui.components

// =============================================================
// Imports
// =============================================================
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.AssistChipDefaults.assistChipColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.eventreminder.ui.viewmodels.EventReminderUI
import com.example.eventreminder.ui.viewmodels.GroupedUiSection
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// =============================================================
// Timber TAG
// =============================================================
private const val TAG = "HomeComponents"

// =============================================================
// HOME SCAFFOLD — now supports bottomBar
// =============================================================
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
                        Icon(Icons.Default.Settings, contentDescription = "Manage Reminders")
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
                Icon(Icons.Default.Add, contentDescription = "Add")
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

// =============================================================
// EMPTY STATE
// =============================================================
@Composable
fun BirthdayEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "No reminders yet. Tap + to add one.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// =============================================================
// EVENTS LIST GROUPED — updated + fixed
// =============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsListGrouped(
    sections: List<GroupedUiSection>,
    onClick: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Timber.tag(TAG).d("Rendering EventsListGrouped")

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutine = rememberCoroutineScope()

    val collapsed = remember { mutableStateMapOf<String, Boolean>() }

    // initialize collapse state
    sections.forEach { section ->
        if (!collapsed.containsKey(section.header)) collapsed[section.header] = false
    }

    Box(modifier.fillMaxSize()) {

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            sections.forEach { section ->

                // HEADER
                stickyHeader {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                collapsed[section.header] =
                                    !(collapsed[section.header] ?: false)
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp, horizontal = 10.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    section.header,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Text(
                                    if (collapsed[section.header] == true) "+" else "–",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }

                // COLLAPSIBLE CONTENT
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

                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Event deleted",
                                                    actionLabel = "Undo"
                                                )

                                                if (result == SnackbarResult.ActionPerformed) {
                                                    // Implement undo if needed
                                                }
                                            }
                                            true
                                        } else false
                                    }
                                )

                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = { SwipeBackgroundM3(dismissState) },
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

        // SNACKBAR
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
        )
    }
}

// =============================================================
// SWIPE DELETE BACKGROUND
// =============================================================
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

// =============================================================
// EVENT CARD
// =============================================================
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
        else -> null
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

            Column(modifier = Modifier.weight(1f)) {

                Text(text = ui.title, style = MaterialTheme.typography.titleSmall)

                ui.description?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = ui.formattedDateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(2.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    repeatLabel?.let {
                        AssistChip(
                            onClick = {},
                            label = { Text(it) },
                            colors = assistChipColors(
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

// =============================================================
// REMAINING TIME FORMATTER
// =============================================================
fun formatTimeRemaining(eventEpoch: Long): String {
    val now = Instant.now()
    val eventInstant = Instant.ofEpochMilli(eventEpoch)
    val duration = Duration.between(now, eventInstant)

    val seconds = duration.seconds
    return when {
        seconds < 0 -> "Elapsed"
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
