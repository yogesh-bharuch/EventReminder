package com.example.eventreminder.ui.modules.dropdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eventreminder.data.model.ReminderTitle
import timber.log.Timber

private const val TAG = "ReminderTitleDropdown"

/**
 * ---------------------------------------------------------
 *  ReminderTitleDropdownModule
 * ---------------------------------------------------------
 *
 * Pure UI component for selecting ReminderTitle.
 * - No business logic
 * - Driven by external state
 * - Returns ReminderTitle to caller
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTitleDropdownModule(
    selectedTitle: ReminderTitle,
    onTitleChanged: (ReminderTitle) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedTitle.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Title") },
            leadingIcon = { Icon(Icons.Default.Edit, null) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ReminderTitle.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        Timber.tag(TAG).d("Title selected: ${option.label}")
                        onTitleChanged(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
