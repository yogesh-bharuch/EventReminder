package com.example.eventreminder.ui.components.swipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SwipeBackgroundM3(state: SwipeToDismissBoxState) {
    val color = when (state.targetValue) {
        SwipeToDismissBoxValue.StartToEnd,
        SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.85f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(20.dp),
        contentAlignment = when (state.dismissDirection) {
            SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
            SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        Icon(
            Icons.Default.Delete,
            contentDescription = "Delete",
            tint = Color.White
        )
    }
}
