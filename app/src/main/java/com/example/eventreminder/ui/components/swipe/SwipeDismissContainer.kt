package com.example.eventreminder.ui.components.swipe

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.RowScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeDismissContainer(
    onDelete: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->

            val isDelete = value == SwipeToDismissBoxValue.StartToEnd ||
                    value == SwipeToDismissBoxValue.EndToStart

            if (isDelete) {
                onDelete()
                // Prevent full dismiss animation; immediately reset
                false
            } else {
                true
            }
        }
    )

    // Ensures the swipe state resets when the composable is recreated
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeBackgroundM3(dismissState) },
        content = content
    )
}
