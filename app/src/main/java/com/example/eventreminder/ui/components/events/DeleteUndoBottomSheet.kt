package com.example.eventreminder.ui.components.events

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import timber.log.Timber
import com.example.eventreminder.logging.DELETE_TAG

// =============================================================
// Delete Undo Bottom Sheet
// =============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteUndoBottomSheet(
    eventTitle: String,
    onUndo: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = {
            Timber.tag(DELETE_TAG).d("⬇ Delete undo sheet dismissed")
            onDismiss()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text(
                text = "Delete event?",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "“$eventTitle” will be removed. You can undo this action now.",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {

                TextButton(
                    onClick = {
                        Timber.tag(DELETE_TAG).d("↩ Undo delete (sheet)")
                        onUndo()
                    }
                ) {
                    Text(text = "Undo")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        Timber.tag(DELETE_TAG).d("🟥 Confirm delete (sheet)")
                        onConfirmDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = "Delete")
                }
            }
        }
    }
}
