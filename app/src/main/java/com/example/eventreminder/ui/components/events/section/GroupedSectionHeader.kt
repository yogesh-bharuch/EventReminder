package com.example.eventreminder.ui.components.events.section

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * -------------------------------------------------------------
 * GroupedSectionHeader
 * -------------------------------------------------------------
 * Header row for grouped reminder sections (Today, Tomorrow, etc.)
 *
 * Responsibilities:
 *  - Display section title
 *  - Show collapsed/expanded state visually
 *  - Toggle state when tapped
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

        // =============================================================
        // HEADER ROW
        // =============================================================
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
                // SECTION TITLE
                // ---------------------------------------------------------
                Text(
                    text = header,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                // ---------------------------------------------------------
                // COLLAPSE / EXPAND ICON
                // ---------------------------------------------------------
                Icon(
                    imageVector = if (isCollapsed)
                        Icons.AutoMirrored.Filled.KeyboardArrowRight   // ▶ collapsed
                    else
                        Icons.Default.KeyboardArrowDown,   // ▼ expanded
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
