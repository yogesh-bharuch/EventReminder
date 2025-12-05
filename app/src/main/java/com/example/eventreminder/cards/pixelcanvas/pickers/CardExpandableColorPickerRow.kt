package com.example.eventreminder.cards.pixelcanvas.pickers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

@Composable
fun CardExpandableColorPickerRow(
    label: String,
    selectedColor: Int?,
    onColorSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val palette = listOf(
        Color.Black, Color.DarkGray, Color.Gray, Color.White,

        Color.Red, Color(0xFFD32F2F), Color(0xFFFF5252),

        Color.Blue, Color(0xFF1565C0), Color(0xFF64B5F6),

        Color.Magenta, Color(0xFFE040FB), Color(0xFF9C27B0),

        Color(0xFFFFC107), Color(0xFFFFA000), Color(0xFFFFE082),

        Color(0xFF4CAF50), Color(0xFF2E7D32), Color(0xFF81C784),

        Color(0xFFFF5722), Color(0xFFFF7043)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {

        Column(
            modifier = Modifier.padding(12.dp)
        ) {

            // ---------- HEADER ROW ----------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.titleMedium)

                    Spacer(Modifier.width(12.dp))

                    // Selected Color Indicator
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                color = selectedColor?.let { Color(it) } ?: Color.Gray,
                                shape = CircleShape
                            )
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            // ---------- EXPANDABLE CONTENT ----------
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
    }
}
