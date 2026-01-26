package com.example.eventreminder.cards.pixelcanvas.pickers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
fun CardColorPickerRow(
    label: String,
    selectedColor: Int?,
    onColorSelected: (Int) -> Unit
) {
    // ⭐ Extended palette — usable for titles, names, dates
    val palette = listOf(
        Color.Black,
        Color.DarkGray,
        Color.Gray,
        Color.White,

        Color.Red,
        Color(0xFFD32F2F), // Dark red
        Color(0xFFFF5252), // Light red

        Color.Blue,
        Color(0xFF1565C0), // Dark blue
        Color(0xFF64B5F6), // Light blue

        Color.Magenta,
        Color(0xFFE040FB), // Light purple
        Color(0xFF9C27B0), // Purple

        Color(0xFFFFC107), // Amber
        Color(0xFFFFA000), // Dark amber
        Color(0xFFFFE082), // Light amber

        Color(0xFF4CAF50), // Green
        Color(0xFF2E7D32), // Dark green
        Color(0xFF81C784), // Light green

        Color(0xFFFF5722), // Deep Orange
        Color(0xFFFF7043), // Light Orange
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(palette) { clr ->
                val isSelected = selectedColor == clr.toArgb()

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(clr, CircleShape)
                        .border(
                            width = if (isSelected) 4.dp else 1.dp,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                Color.LightGray,
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(clr.toArgb()) }
                )
            }
        }
    }
}
