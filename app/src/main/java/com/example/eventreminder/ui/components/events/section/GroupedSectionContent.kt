package com.example.eventreminder.ui.components.events.section

// =============================================================
// Imports
// =============================================================
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.eventreminder.ui.viewmodels.EventReminderUI
import com.example.eventreminder.ui.components.cards.EventCard
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import com.example.eventreminder.ui.components.events.DeleteUndoBottomSheet
import kotlinx.coroutines.launch
import timber.log.Timber
import com.example.eventreminder.logging.DELETE_TAG

// =============================================================
// Delete lock (prevents double-trigger)
// =============================================================
private val deleteLock = mutableSetOf<String>()

// =============================================================
// Swipe Delete Container
// =============================================================
@Composable
private fun SwipeDeleteContainer(
    id: String,
    onSwipeDelete: () -> Unit,
    content: @Composable () -> Unit
) {
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
                        if (dragAmount < -threshold) {
                            onSwipeDelete()
                        }
                        dragAmount = 0f
                    },
                    onHorizontalDrag = { _, drag ->
                        dragAmount = (dragAmount + drag).coerceAtMost(0f)
                    }
                )
            }
    ) {

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(end = 20.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = "Delete",
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        Box(
            modifier = Modifier.offset(x = animatedOffset.dp)
        ) {
            content()
        }
    }
}

// =============================================================
// Grouped Section Content (BOTTOM SHEET ONLY)
// =============================================================
@Composable
fun GroupedSectionContent(
    events: List<EventReminderUI>,
    collapsed: Boolean,
    viewModel: ReminderViewModel,
    onClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var pendingDeleteUi by remember { mutableStateOf<EventReminderUI?>(null) }

    // ---- Bottom sheet (single confirmation gate) ----
    if (pendingDeleteUi != null) {
        DeleteUndoBottomSheet(
            eventTitle = pendingDeleteUi!!.title,
            onUndo = {
                Timber.tag(DELETE_TAG).d("↩ Undo delete id=${pendingDeleteUi!!.id}")
                pendingDeleteUi = null
            },
            onConfirmDelete = {
                val id = pendingDeleteUi!!.id
                pendingDeleteUi = null

                if (!deleteLock.add(id)) return@DeleteUndoBottomSheet

                scope.launch {
                    Timber.tag(DELETE_TAG).d("🟥 Final delete id=$id")
                    viewModel.deleteEventWithUndo(id)
                    deleteLock.remove(id)
                }
            },
            onDismiss = {
                Timber.tag(DELETE_TAG).d("⬇ Delete sheet dismissed")
                pendingDeleteUi = null
            }
        )
    }

    AnimatedVisibility(
        visible = !collapsed,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            events.forEach { ui ->

                SwipeDeleteContainer(
                    id = ui.id,
                    onSwipeDelete = {
                        Timber.tag(DELETE_TAG).d("🟥 Swipe → open delete sheet id=${ui.id}")
                        pendingDeleteUi = ui
                    }
                ) {

                    EventCard(
                        ui = ui,
                        onClick = { onClick(ui.id) },
                        onDelete = {
                            Timber.tag(DELETE_TAG).d("🟥 Icon → open delete sheet id=${ui.id}")
                            pendingDeleteUi = ui
                        }
                    )
                }
            }
        }
    }
}
