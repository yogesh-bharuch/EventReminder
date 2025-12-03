package com.example.eventreminder.pdf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext


@HiltViewModel
class PdfViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val pdfGenerator: PdfGenerator,
    private val realReportBuilder: RealReportBuilder
) : ViewModel() {

    // ---------------------------------------------------------
    // StateFlows for each PDF
    // ---------------------------------------------------------
    private val _pdfUri = MutableStateFlow<Uri?>(null)
    val pdfUri = _pdfUri.asStateFlow()

    private val _todo3PdfUri = MutableStateFlow<Uri?>(null)
    val todo3PdfUri = _todo3PdfUri.asStateFlow()

    // ---------------------------------------------------------
    // Auto-open one-time event
    // ---------------------------------------------------------
    private val _openPdfEvent = Channel<Uri>(Channel.BUFFERED)
    val openPdfEvent = _openPdfEvent.receiveAsFlow()

    // =========================================================
    // REAL DB DATA → PDF REPORT
    // =========================================================
    fun runTodo3RealReport() {
        viewModelScope.launch {
            val report = realReportBuilder.buildReport()
            val uri = pdfGenerator.generateReportPdf(appContext, report).getOrNull()
            _todo3PdfUri.value = uri
            if (uri != null) {
                _openPdfEvent.send(uri)
                Log.d("PDF URI", "Pdf file: $uri")
            }
        }
    }

}