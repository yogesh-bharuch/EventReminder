package com.example.eventreminder.ui.screens

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    var showConfirmDialog by remember { mutableStateOf(false) }
    var retentionDaysInput by remember { mutableStateOf("30") }

    val retentionDays = retentionDaysInput.toIntOrNull()?.coerceIn(0, 365) ?: 30

    val gcReport by viewModel.gcReport.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Text(text = "Reminder Manager", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(24.dp))

        // ---------------------------------------------------------
        // Retention Days Input
        // ---------------------------------------------------------
        OutlinedTextField(
            enabled = !isRunning,
            value = retentionDaysInput,
            onValueChange = { retentionDaysInput = it },
            label = { Text("Tombstone retention (days 0-365)") },
            singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        // ---------------------------------------------------------
        // Tombstone GC Button
        // ---------------------------------------------------------
        Button(
            enabled = !isRunning,
            onClick = { showConfirmDialog = true }
        ) {
            Text(if (isRunning) "Cleanup Running…" else "Run Reminder Cleanup")
        }

        // ---------------------------------------------------------
        // GC Report Summary
        // ---------------------------------------------------------
        gcReport?.let { report ->
            Spacer(Modifier.height(24.dp))
            TombstoneGcReportSummary(report = report)
        }
    }

    // ---------------------------------------------------------
    // Confirmation Dialog
    // ---------------------------------------------------------
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        viewModel.runFullCleanup(retentionDays = retentionDays)
                    }
                ) {
                    Text("Run Cleanup")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Confirm Tombstone Cleanup") },
            text = {
                Text(
                    "This will:\n\n" +
                            "• Move one-time past reminders older than $retentionDays days to expired\n" +
                            "• Permanently delete tombstones older than $retentionDays days\n\n" +
                            "This action cannot be undone."
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
