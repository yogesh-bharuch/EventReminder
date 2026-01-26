package com.example.eventreminder.ui.modules.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import timber.log.Timber

private const val TAG = "ReminderSaveButtonModule"

/**
 * ---------------------------------------------------------
 *  ReminderSaveButtonModule
 * ---------------------------------------------------------
 *
 * Dumb UI button that triggers save action from screen.
 * The screen calls VM's onSaveClicked() and VM handles:
 *   - validation
 *   - mapping fields
 *   - constructing EventReminder
 *   - Room upsert
 *   - scheduling alarms
 *   - emitting success events
 */
@Composable
fun ReminderSaveButtonModule(
    isEditMode: Boolean,
    modifier: Modifier = Modifier,
    onSave: () -> Unit
) {
    Button(
        onClick = {
            Timber.tag(TAG).d("Save clicked (editMode=$isEditMode)")
            onSave()
        },
        modifier = modifier
    ) {
        Icon(Icons.Default.Save, null)
        Text(if (isEditMode) "Update" else "Save")
    }
}
