package com.example.eventreminder.ui.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.eventreminder.pdf.PdfViewModel
import com.example.eventreminder.util.openPdf


@Composable
fun DebugScreen(
    viewModel: PdfViewModel = hiltViewModel()
) {
    // ----------- Observers for all PDF test flows -----------
    val uri by viewModel.pdfUri.collectAsStateWithLifecycle()
    val todo1Uri by viewModel.todo1PdfUri.collectAsStateWithLifecycle()
    val todo2Uri by viewModel.todo2PdfUri.collectAsStateWithLifecycle()

    // ⭐ NEW - TODO-3 REAL REPORT
    val todo3Uri by viewModel.todo3PdfUri.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Auto-open listener
    LaunchedEffect(Unit) {
        viewModel.openPdfEvent.collect { pdfUri ->
            openPdf(context, pdfUri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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

        // ----------------------------------------------------
        // MAIN CARD
        // ----------------------------------------------------
        Card(
            modifier = Modifier.fillMaxWidth(),
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

                // =========================================================
                // TODO-0: Blank PDF
                // =========================================================
                Button(onClick = { viewModel.runBlankPdfTest() }) {
                    Text("Run PDF Test (TODO-0)")
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (uri != null) {
                    Text("PDF saved at:\n$uri", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { openPdf(context, uri!!) }) {
                        Text("Open PDF")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // =========================================================
                // TODO-1: Fake Data Report
                // =========================================================
                Button(onClick = { viewModel.runTodo1Test() }) {
                    Text("Run TODO-1 PDF Test")
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (todo1Uri != null) {
                    Text("TODO-1 PDF saved at:\n$todo1Uri", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { openPdf(context, todo1Uri!!) }) {
                        Text("Open TODO-1 PDF")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // =========================================================
                // TODO-2: Styled Fake Data Report
                // =========================================================
                Button(onClick = { viewModel.runTodo2Test() }) {
                    Text("Run TODO-2 (Modern Report)")
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (todo2Uri != null) {
                    Text("TODO-2 PDF saved at:\n$todo2Uri", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { openPdf(context, todo2Uri!!) }) {
                        Text("Open TODO-2 PDF")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // =========================================================
                // ⭐ TODO-3: REAL REPORT (LIVE DATABASE)
                // =========================================================
                Button(onClick = { viewModel.runTodo3RealReport() }) {
                    Text("Run TODO-3 (REAL DATA REPORT)")
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (todo3Uri != null) {
                    Text("TODO-3 PDF saved at:\n$todo3Uri", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { openPdf(context, todo3Uri!!) }) {
                        Text("Open REAL TODO-3 PDF")
                    }
                }
            }
        }
    }
}
