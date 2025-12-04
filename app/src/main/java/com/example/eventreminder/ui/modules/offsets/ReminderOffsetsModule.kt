package com.example.eventreminder.ui.modules.offsets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eventreminder.data.model.ReminderOffset
import timber.log.Timber

private const val TAG = "ReminderOffsetsModule"

/**
 * ReminderOffsetsModule — Compact Spacing Version
 */
@Composable
fun ReminderOffsetsModule(
    selectedOffsets: Set<ReminderOffset>,
    onOffsetsChanged: (Set<ReminderOffset>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {

        // Header
        Text(
            text = "Reminder Alerts",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(2.dp))   // 🔥 Minimum spacing before rows

        // Checkboxes
        ReminderOffset.entries.forEach { offset ->

            val checked = offset in selectedOffsets

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),    // 🔥 Minimum vertical padding
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        val updated = selectedOffsets.toMutableSet().apply {
                            if (isChecked) add(offset) else remove(offset)
                        }

                        Timber.tag(TAG).d(
                            "Offset %s %s → size=%d",
                            offset.label,
                            if (isChecked) "added" else "removed",
                            updated.size
                        )

                        onOffsetsChanged(updated)
                    }
                )

                Spacer(modifier = Modifier.width(6.dp)) // Slight reduction (8 → 6)

                Text(
                    text = offset.label,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
