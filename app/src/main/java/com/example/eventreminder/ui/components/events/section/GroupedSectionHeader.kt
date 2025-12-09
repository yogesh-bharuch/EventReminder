package com.example.eventreminder.ui.components.events.section

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * -------------------------------------------------------------
 * GroupedSectionHeader
 * -------------------------------------------------------------
 * Displays the sticky header for each section:
 *   - Icon + title
 *   - Count pill badge
 *   - Collapse/expand arrow
 */
@Composable
fun GroupedSectionHeader(
    header: String,
    count: Int,                     // ⭐ NEW
    isCollapsed: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 10.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                // ---------------------------------------------------------
                // LEFT: icon + title + count-pill
                // ---------------------------------------------------------
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = iconForSection(header),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = header,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // ⭐ COUNT PILL BADGE
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = count.toString(),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // ---------------------------------------------------------
                // RIGHT: EXPAND / COLLAPSE ARROW
                // ---------------------------------------------------------
                Icon(
                    imageVector = if (isCollapsed)
                        Icons.AutoMirrored.Filled.KeyboardArrowRight  // ▶ collapsed
                    else
                        Icons.Default.KeyboardArrowDown,               // ▼ expanded
                    contentDescription = if (isCollapsed)
                        "Expand section" else "Collapse section",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Divider below header
            HorizontalDivider()
        }
    }
}
