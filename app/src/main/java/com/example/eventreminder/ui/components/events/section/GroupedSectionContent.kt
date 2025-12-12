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
import timber.log.Timber
import com.example.eventreminder.logging.DELETE_TAG
import kotlinx.coroutines.delay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupedSectionContent(
    events: List<EventReminderUI>,
    collapsed: Boolean,
    viewModel: ReminderViewModel,
    snackbarHostState: SnackbarHostState,
    onClick: (String) -> Unit      // <-- FIXED to String UUID
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

                            Timber.tag(DELETE_TAG)
                                .d("🟥 Initiator(Swipe) → delete id=${ui.id}")

                            viewModel.deleteEventWithUndo(ui.id)

                            // ⭐ Small delay prevents coroutine cancellation
                            delay(200)

                            val result = snackbarHostState.showSnackbar(
                                message = "Event deleted",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )

                            if (result == SnackbarResult.ActionPerformed) {

                                Timber.tag(DELETE_TAG)
                                    .d("↩ Undo triggered → restore id=${ui.id}")

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

                                Timber.tag(DELETE_TAG)
                                    .d("🟥 Initiator(CardButton) → delete id=${ui.id}")

                                viewModel.deleteEventWithUndo(ui.id)

                                // ⭐ Small delay prevents coroutine cancellation
                                delay(200)

                                val result = snackbarHostState.showSnackbar(
                                    message = "Event deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short
                                )

                                if (result == SnackbarResult.ActionPerformed) {

                                    Timber.tag(DELETE_TAG)
                                        .d("↩ Undo triggered → restore id=${ui.id}")

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
