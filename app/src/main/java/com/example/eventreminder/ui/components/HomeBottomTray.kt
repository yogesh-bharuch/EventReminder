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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Download
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
    isSyncing: Boolean,
    isBackingUp: Boolean,
    isRestoring: Boolean,
    isGeneratingPdf: Boolean,
    onCleanupClick: () -> Unit,
    onGeneratePdfClick: () -> Unit,
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

            item {
                ActionChip(
                    label = if (isGeneratingPdf) "Generating PDF" else "PDF Report",
                    icon = if (isGeneratingPdf) null else Icons.Default.PictureAsPdf,
                    enabled = !isGeneratingPdf,
                    trailing = {
                        if (isGeneratingPdf) {
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

            item {
                ActionChip(
                    label = "Export",
                    icon = Icons.Default.Download,
                    onClick = {
                        Timber.tag(TAG).d("Export clicked")
                        onExportClick()
                    }
                )
            }

            item {
                ActionChip(
                    label = if (isSyncing) "Syncing…" else "Sync",
                    icon = if (isSyncing) null else Icons.Default.Sync,
                    enabled = !isSyncing,
                    trailing = {
                        if (isSyncing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    onClick = {
                        Timber.tag(TAG).d("Sync clicked (isSyncing=$isSyncing)")
                        onSyncClick()
                    }
                )
            }


            item {
                ActionChip(
                    label = if (isBackingUp) "Backing up…" else "Backup",
                    icon = if (isBackingUp) null else Icons.Default.Backup,
                    enabled = !isBackingUp,
                    trailing = {
                        if (isBackingUp) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    onClick = { onBackupClick() }
                )
            }

            item {
                ActionChip(
                    label = if (isRestoring) "Restoring…" else "Restore",
                    icon = if (isRestoring) null else Icons.Default.Restore,
                    enabled = !isRestoring,
                    trailing = {
                        if (isRestoring) {
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
