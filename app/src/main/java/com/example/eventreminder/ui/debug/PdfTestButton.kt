package com.example.eventreminder.ui.debug

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventreminder.pdf.PdfViewModel

@Composable
fun PdfTestButton(viewModel: PdfViewModel = hiltViewModel()) {

    val uri by viewModel.pdfUri.collectAsState()

    Column {
        Button(onClick = { viewModel.runBlankPdfTest() }) {
            Text("Run PDF Test (TODO-0)")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uri != null) {
            Text("PDF saved at: $uri")
        }
    }
}
