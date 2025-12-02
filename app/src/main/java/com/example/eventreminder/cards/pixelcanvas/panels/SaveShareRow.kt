package com.example.eventreminder.cards.pixelcanvas.panels

// =============================================================
// SaveShareRow — UI module for Save PNG and Share PNG actions
// - Stateless, pure composable
// =============================================================

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SaveShareRow(
    onSaveClicked: () -> Unit,
    onShareClicked: () -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = onSaveClicked) {
            Text("Save PNG")
        }

        Button(onClick = onShareClicked) {
            Text("Share PNG")
        }
    }
}
