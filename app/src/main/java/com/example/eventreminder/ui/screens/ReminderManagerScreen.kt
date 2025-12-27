package com.example.eventreminder.ui.screens

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventreminder.maintenance.gc.TombstoneGcReport
import com.example.eventreminder.ui.viewmodels.ReminderManagerViewModel

@Composable
fun ReminderManagerScreen(
    onBack: () -> Unit,
    onOpenDebug: () -> Unit,
    viewModel: ReminderManagerViewModel = hiltViewModel()
) {

    // ---------------------------------------------------------
    // UI-only State
    // ---------------------------------------------------------
    var retentionDaysInput by remember { mutableStateOf("30") }
    var showGcConfirmDialog by remember { mutableStateOf(false) }

    val retentionDays = retentionDaysInput.toIntOrNull()?.coerceIn(0, 365) ?: 30

    val gcReport by viewModel.gcReport.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // =====================================================
        // Header
        // =====================================================
        item {
            Text(
                text = "Reminder Manager",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        // =====================================================
        // Retention Days Input
        // =====================================================
        item {
            OutlinedTextField(
                enabled = !isRunning,
                value = retentionDaysInput,
                onValueChange = { retentionDaysInput = it },
                label = { Text("Retention window (days 0–365)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // =====================================================
        // Phase A — Propagate Tombstones
        // =====================================================
        item {
            Text(
                text = "Phase A — Propagate tombstones",
                style = MaterialTheme.typography.titleMedium
            )
        }

        item {
            Text(
                text =
                    "• Marks expired one-time reminders as tombstones\n" +
                            "• Syncs tombstones to Firestore\n" +
                            "• Safe to run multiple times\n" +
                            "• Required before permanent deletion",
                style = MaterialTheme.typography.bodySmall
            )
        }

        item {
            Button(
                enabled = !isRunning,
                onClick = {
                    viewModel.propagateExpiredTombstones(
                        retentionDays = retentionDays
                    )
                }
            ) {
                Text("Run Phase A (Propagate)")
            }
        }

        // =====================================================
        // Phase B — Permanent Delete
        // =====================================================
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Text(
                text = "Phase B — Permanent cleanup",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        item {
            Text(
                text =
                    "⚠ WARNING:\n" +
                            "• Run only AFTER all devices have synced\n" +
                            "• Removes deleted  records from device\n" +
                            "• Permanently deletes tombstones in Remote\n" +
                            "• This action CANNOT be undone",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Red
            )
        }

        item {
            Button(
                enabled = !isRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                onClick = { showGcConfirmDialog = true }
            ) {
                Text("Run Phase B (Permanent Delete)")
            }
        }

        // =====================================================
        // GC Report Summary (Optional)
        // =====================================================
        gcReport?.let { report ->
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                TombstoneGcReportSummary(report = report)
            }
        }
    }

    // =========================================================
    // Confirmation Dialog (Phase B only)
    // =========================================================
    if (showGcConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showGcConfirmDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGcConfirmDialog = false
                        viewModel.runRemoteTombstoneGc(
                            retentionDays = retentionDays
                        )
                    }
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGcConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Confirm Permanent Deletion") },
            text = {
                Text(
                    "This will permanently delete tombstones older than $retentionDays days.\n\n" +
                            "Make sure ALL devices have synced before proceeding."
                )
            }
        )
    }
}

@Composable
private fun TombstoneGcReportSummary(
    report: TombstoneGcReport
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Cleanup Summary",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))

        Text("Retention window: ${report.retentionDays} days")
        Text("Local candidates: ${report.localCandidates}")
        Text("Remote candidates: ${report.remoteCandidates}")
        Text("Deleted locally: ${report.deletedLocal}")
        Text("Deleted remotely: ${report.deletedRemote}")

        if (report.failedRemoteDeletes > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "⚠ Remote delete failures: ${report.failedRemoteDeletes}",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
