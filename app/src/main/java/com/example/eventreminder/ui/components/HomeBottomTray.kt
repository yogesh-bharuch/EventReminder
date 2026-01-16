package com.example.eventreminder.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import timber.log.Timber

private const val TAG = "HomeBottomTray"

/*// =============================================================
// Public API: HomeBottomTray
// - A horizontal tray of action chips shown at bottom of Home screen
// - Buttons are composable items in a LazyRow so they can overflow/scoll
// =============================================================*/

/**
 * HomeBottomTray
 *
 * @param onCleanupClick         invoked when user taps "Cleanup"
 * @param onGeneratePdfClick     invoked when user taps "PDF Report"
 * @param onExportClick          invoked when user taps "Export"
 * @param onSyncClick            invoked when user taps "Sync"
 *
 * Keep these handlers thin; call into your ViewModel/Repository for real work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeBottomTray(
    isWorking: Boolean,
    isWorkingPDF: Boolean,
    onCleanupClick: () -> Unit,
    onGeneratePdfClick: () -> Unit,
    on7DaysPdfClick: () -> Unit,
    onExportClick: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    Timber.tag(TAG).d("Rendering HomeBottomTray")

    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            //Debug
            item {
                ActionChip(
                    label = "Debug", //"Cleanup",
                    icon = Icons.Default.CleaningServices,
                    onClick = {
                        Timber.tag(TAG).d("Cleanup clicked")
                        onCleanupClick()
                    }
                )
            }

            //PDF Report
            item {
                ActionChip(
                    label = if (isWorkingPDF) "Working" else "PDF Report",
                    icon = Icons.Default.PictureAsPdf,
                    enabled = !isWorkingPDF,
                    trailing = {
                        if (isWorkingPDF) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    onClick = {
                        Timber.tag(TAG).d("PDF Report clicked")
                        onGeneratePdfClick()
                    }
                )
            }

            //7Days Reminders
            item {
                ActionChip(
                    label = if (isWorkingPDF) "Working" else "7Days Reminders",
                    icon = Icons.Default.CalendarViewWeek,
                    enabled = !isWorkingPDF,
                    trailing = {
                        if (isWorkingPDF) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    onClick = {
                        Timber.tag(TAG).d("7Days Reminders clicked")
                        on7DaysPdfClick()
                    }
                )
            }

            //Export
            item {
                ActionChip(
                    label = if (isWorkingPDF) "Working" else "Export",
                    icon = Icons.Default.ImportExport,
                    enabled = !isWorkingPDF,
                    trailing = {
                        if (isWorkingPDF) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    onClick = {
                        Timber.tag(TAG).d("Export clicked")
                        onExportClick()
                    }
                )
            }

            //Sync
            item {
                ActionChip(
                    label = if (isWorking) "Working" else "Sync",
                    icon = if (isWorking) null else Icons.Default.Sync,
                    enabled = !isWorking,
                    trailing = {
                        if (isWorking) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    onClick = {
                        Timber.tag(TAG).d("Sync clicked (isSyncing=$isWorking)")
                        onSyncClick()
                    }
                )
            }

            //Backup
            item {
                ActionChip(
                    label = if (isWorking) "Working" else "Backup",
                    icon = if (isWorking) null else Icons.Default.Backup,
                    enabled = !isWorking,
                    trailing = {
                        if (isWorking) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    onClick = { onBackupClick() }
                )
            }

            //Restore
            item {
                ActionChip(
                    label = if (isWorking) "Working" else "Restore",
                    icon = if (isWorking) null else Icons.Default.Restore,
                    enabled = !isWorking,
                    trailing = {
                        if (isWorking) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    onClick = { onRestoreClick() }
                )
            }
        }
    }
}

// =============================================================
// Small composable: ActionChip
// - Label + optional icon, rounded pill style
// =============================================================
@Composable
private fun ActionChip(
    label: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    height: Dp = 40.dp,
    horizontalPadding: Dp = 16.dp
) {
    val backgroundColor =
        if (enabled) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant

    val contentColor =
        if (enabled) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = if (enabled) 3.dp else 0.dp,
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = horizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = "$label icon",
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )

            if (trailing != null) {
                trailing()
            }
        }
    }
}
