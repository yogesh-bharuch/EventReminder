package com.example.eventreminder.ui.modules.offsets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eventreminder.data.model.ReminderOffset
import timber.log.Timber

private const val TAG = "ReminderOffsetsModule"

/**
 * ---------------------------------------------------------
 *  ReminderOffsetsModule
 * ---------------------------------------------------------
 *
 * Displays the "Reminder Alerts" header and a list of
 * checkboxes for all ReminderOffset options.
 *
 * Pure UI:
 *  - Driven by caller's selectedOffsets
 *  - Emits new Set<ReminderOffset> via onOffsetsChanged
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

        // All offset options
        ReminderOffset.entries.forEach { offset ->
            val checked = offset in selectedOffsets

            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        val updated = selectedOffsets.toMutableSet().apply {
                            if (isChecked) add(offset) else remove(offset)
                        }
                        Timber.tag(TAG).d(
                            "Offset %s %s, new size=%d",
                            offset.label,
                            if (isChecked) "added" else "removed",
                            updated.size
                        )
                        onOffsetsChanged(updated)
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = offset.label)
            }
        }
    }
}
