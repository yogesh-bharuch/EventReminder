package com.example.eventreminder.ui.modules.dropdown

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.eventreminder.data.model.RepeatRule
import timber.log.Timber

private const val TAG = "RepeatRuleDropdown"

/**
 * ---------------------------------------------------------
 *  ReminderRepeatRuleDropdownModule
 * ---------------------------------------------------------
 *
 * Pure UI dropdown for selecting RepeatRule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderRepeatRuleDropdownModule(
    selectedRule: RepeatRule,
    onRuleChanged: (RepeatRule) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Text("Repeat Rule")

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedRule.label,
            onValueChange = {},
            readOnly = true,
            leadingIcon = { Icon(Icons.Default.Repeat, null) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            RepeatRule.entries.forEach { rule ->
                DropdownMenuItem(
                    text = { Text(rule.label) },
                    onClick = {
                        Timber.tag(TAG).d("Repeat rule selected: ${rule.label}")
                        onRuleChanged(rule)
                        expanded = false
                    }
                )
            }
        }
    }
}
