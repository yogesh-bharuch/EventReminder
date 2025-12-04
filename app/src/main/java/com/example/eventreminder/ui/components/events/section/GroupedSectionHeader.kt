package com.example.eventreminder.ui.components.events.section

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * -------------------------------------------------------------
 * GroupedSectionHeader
 * -------------------------------------------------------------
 * Displays the section header (Today, Tomorrow, Upcoming, etc.)
 * with:
 *  - Left: section-specific icon
 *  - Center: title
 *  - Right: expand/collapse arrow
 *
 * Responsibilities:
 *  - Toggle collapse state
 *  - Remain sticky in LazyColumn
 *  - Provide a clean clickable header row
 *
 * Called from:
 *  - EventsListGrouped
 */
@Composable
fun GroupedSectionHeader(
    header: String,
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                // ---------------------------------------------------------
                // LEFT: icon + section title
                // ---------------------------------------------------------
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically   // ⭐ NEW: align icon + text perfectly
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
                        "Expand section"
                    else
                        "Collapse section",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Divider below header
            HorizontalDivider()
        }
    }
}
