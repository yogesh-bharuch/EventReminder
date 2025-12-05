package com.example.eventreminder.cards.pixelcanvas.modules

// =============================================================
// TitleColorPickerModule.kt
// A reusable color picker row for selecting title text color
// =============================================================

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

@Composable
fun TitleColorPickerModule(
    selectedColorArgb: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        Color.Black,
        Color.White,
        Color.Red,
        Color.Blue,
        Color.Magenta,
        Color(0xFFFFC107), // Amber
        Color(0xFF4CAF50), // Green
    )

    Column(modifier = modifier) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text("Title Color:", style = MaterialTheme.typography.bodyMedium)

            colors.forEach { clr ->
                val isSelected = (selectedColorArgb == clr.toArgb())

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(clr, CircleShape)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(clr.toArgb()) }
                )
            }
        }
    }
}
