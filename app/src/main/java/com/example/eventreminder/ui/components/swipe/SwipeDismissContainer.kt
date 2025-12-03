package com.example.eventreminder.ui.components.swipe

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.RowScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeDismissContainer(
    onDelete: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart ||
                value == SwipeToDismissBoxValue.StartToEnd
            ) {
                onDelete()
                false // prevent default dismiss animation
            } else true
        }
    )

    // Reset red background when section collapses/expands
    LaunchedEffect(Unit) {
        dismissState.snapTo(SwipeToDismissBoxValue.Settled)
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = { SwipeBackgroundM3(dismissState) },
        content = content
    )
}
