package com.example.eventreminder.ui.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventreminder.pdf.PdfViewModel
import androidx.compose.material3.Button

/**
 * =============================================================
 * DebugScreen
 * - Central place for all developer tools
 * - TODO-0: Blank PDF creation test
 * - Future features: logs, test alarms, DB dump, etc.
 * =============================================================
 */
@Composable
fun DebugScreen(
    viewModel: PdfViewModel = hiltViewModel()
) {
    val uri by viewModel.pdfUri.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Developer Tools",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Use these tools for debugging and feature tests.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        // -------------------------
        // SECTION: PDF TEST CARD
        // -------------------------
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                Text(
                    text = "PDF Generator Test",
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = { viewModel.runBlankPdfTest() }) {
                    Text("Run PDF Test (TODO-0)")
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (uri != null) {
                    Text(
                        text = "PDF saved at:\n$uri",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
