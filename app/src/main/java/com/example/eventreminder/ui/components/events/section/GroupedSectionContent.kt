package com.example.eventreminder.ui.components.events.section

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import com.example.eventreminder.ui.viewmodels.EventReminderUI
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import com.example.eventreminder.ui.components.cards.EventCard
import com.example.eventreminder.ui.components.swipe.SwipeDismissContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupedSectionContent(
    events: List<EventReminderUI>,
    collapsed: Boolean,
    viewModel: ReminderViewModel,
    snackbarHostState: SnackbarHostState,
    onClick: (Long) -> Unit
) {
    val coroutine = rememberCoroutineScope()

    AnimatedVisibility(
        visible = !collapsed,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            events.forEach { ui ->

                SwipeDismissContainer(
                    onDelete = {
                        coroutine.launch {
                            viewModel.deleteEventWithUndo(ui.id)

                            val result = snackbarHostState.showSnackbar(
                                "Event deleted",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )

                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.restoreLastDeleted()
                            }
                        }
                    }
                ) {
                    EventCard(
                        ui = ui,
                        onClick = { onClick(ui.id) },
                        onDelete = {
                            coroutine.launch {
                                viewModel.deleteEventWithUndo(ui.id)
                                val result = snackbarHostState.showSnackbar(
                                    "Event deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restoreLastDeleted()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
