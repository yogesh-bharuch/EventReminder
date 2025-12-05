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

// =============================================================
// Timber TAG
// =============================================================
private const val TAG = "HomeBottomTray"

// =============================================================
// Public API: HomeBottomTray
// - A horizontal tray of action chips shown at bottom of Home screen
// - Buttons are composable items in a LazyRow so they can overflow/scoll
// =============================================================

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
                    label = "Cleanup",
                    icon = Icons.Default.CleaningServices,
                    onClick = {
                        Timber.tag(TAG).d("Cleanup clicked")
                        onCleanupClick()
                    }
                )
            }

            item {
                ActionChip(
                    label = "PDF Report",
                    icon = Icons.Default.PictureAsPdf,
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
                    label = "Sync",
                    icon = Icons.Default.Sync,
                    onClick = {
                        Timber.tag(TAG).d("Sync clicked")
                        onSyncClick()
                    }
                )
            }

            item {
                ActionChip(
                    label = "Backup",
                    icon = Icons.Default.Backup,
                    onClick = {
                        onBackupClick()
                    }
                )
            }

            item {
                ActionChip(
                    label = "Restore",
                    icon = Icons.Default.Restore,
                    onClick = {
                        onRestoreClick()
                    }
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
    onClick: () -> Unit,
    height: Dp = 40.dp,
    horizontalPadding: Dp = 16.dp
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 3.dp,
        modifier = Modifier
            .clickable { onClick() }
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
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
