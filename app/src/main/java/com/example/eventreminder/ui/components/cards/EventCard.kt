package com.example.eventreminder.ui.components.cards

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eventreminder.ui.viewmodels.EventReminderUI

// =============================================================
// EVENT CARD
// =============================================================
@Composable
fun EventCard(
    ui: EventReminderUI,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val repeatLabel = when (ui.repeatRule) {
        "every_minute" -> "Every Minute"
        "daily" -> "Daily"
        "weekly" -> "Weekly"
        "monthly" -> "Monthly"
        "yearly" -> "Yearly"
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {

                Text(ui.title, style = MaterialTheme.typography.titleSmall)

                ui.description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    ui.formattedDateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(2.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    repeatLabel?.let {
                        AssistChip(
                            onClick = {},
                            label = { Text(it) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }

                    Text(
                        "⏳ ${ui.timeRemainingLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
