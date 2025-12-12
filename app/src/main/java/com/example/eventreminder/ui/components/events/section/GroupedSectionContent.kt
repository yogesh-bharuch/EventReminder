package com.example.eventreminder.ui.components.events.section

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.eventreminder.ui.viewmodels.EventReminderUI
import com.example.eventreminder.ui.components.cards.EventCard
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import timber.log.Timber
import com.example.eventreminder.logging.DELETE_TAG

// Local delete-lock (prevents double delete triggers)
private val deleteLock = mutableSetOf<String>()


@Composable
private fun SwipeDeleteContainer(
    id: String,
    onDelete: suspend () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()

    var dragAmount by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(
        targetValue = dragAmount,
        animationSpec = tween(durationMillis = 200),
        label = ""
    )

    val threshold = 180f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragAmount < -threshold && deleteLock.add(id)) {
                            scope.launch {
                                onDelete()
                                delay(200)
                                deleteLock.remove(id)
                            }
                        }
                        dragAmount = 0f
                    },
                    onHorizontalDrag = { _, drag ->
                        dragAmount = (dragAmount + drag).coerceAtMost(0f)
                    }
                )
            }
    ) {

        // background (red)
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(end = 20.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                "Delete",
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        // content sliding
        Box(
            Modifier
                .offset(x = animatedOffset.dp)
        ) {
            content()
        }
    }
}


@Composable
fun GroupedSectionContent(
    events: List<EventReminderUI>,
    collapsed: Boolean,
    viewModel: ReminderViewModel,
    snackbarHostState: SnackbarHostState,
    onClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    AnimatedVisibility(
        visible = !collapsed,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            events.forEach { ui ->

                SwipeDeleteContainer(
                    id = ui.id,
                    onDelete = {
                        Timber.tag(DELETE_TAG).d("🟥 Swipe → delete id=${ui.id}")
                        viewModel.deleteEventWithUndo(ui.id)

                        delay(160)

                        val result = snackbarHostState.showSnackbar(
                            message = "Event deleted",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Long
                        )

                        if (result == SnackbarResult.ActionPerformed) {
                            Timber.tag(DELETE_TAG).d("↩ Undo → restore id=${ui.id}")
                            viewModel.restoreLastDeleted()
                        }
                    }
                ) {

                    EventCard(
                        ui = ui,
                        onClick = { onClick(ui.id) },
                        onDelete = {
                            if (!deleteLock.add(ui.id)) return@EventCard

                            scope.launch {
                                Timber.tag(DELETE_TAG).d("🟥 Icon → delete id=${ui.id}")
                                viewModel.deleteEventWithUndo(ui.id)

                                delay(160)

                                val result = snackbarHostState.showSnackbar(
                                    message = "Event deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Long
                                )

                                if (result == SnackbarResult.ActionPerformed) {
                                    Timber.tag(DELETE_TAG).d("↩ Undo → restore id=${ui.id}")
                                    viewModel.restoreLastDeleted()
                                }

                                deleteLock.remove(ui.id)
                            }
                        }
                    )
                }
            }
        }
    }
}
