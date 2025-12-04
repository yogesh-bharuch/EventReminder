package com.example.eventreminder.ui.components.events.section

import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Update
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps section header name → appropriate Material3 icon.
 * Add new sections here whenever grouping expands.
 */
fun iconForSection(header: String): ImageVector {
    return when (header.lowercase()) {
        "today" -> Icons.Filled.Today
        "tomorrow" -> Icons.Filled.WbSunny
        "next 7 days" -> Icons.Filled.CalendarMonth
        "next 30 days" -> Icons.Filled.Event
        "upcoming" -> Icons.Filled.Update
        "past 7 days" -> Icons.Filled.History
        "archives" -> Icons.Filled.Archive
        else -> Icons.Filled.Event   // default fallback
    }
}