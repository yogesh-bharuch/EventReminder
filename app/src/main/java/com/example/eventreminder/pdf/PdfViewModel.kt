package com.example.eventreminder.pdf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * =============================================================
 * PdfViewModel
 *
 * Supports:
 *  - TODO-0  → Blank PDF
 *  - TODO-1  → Fake-data PDF
 *  - TODO-2  → Modern styled PDF (fake data)
 *  - TODO-3  → REAL DB-based PDF report
 *
 * Auto-open works for all PDF generations.
 * =============================================================
 */
@HiltViewModel
class PdfViewModel @Inject constructor(
    private val pdfTestGenerator: PdfTestGenerator,
    private val todo1Generator: PdfTodo1Generator,
    private val todo2Generator: PdfTodo2Generator,
    private val realReportBuilder: RealReportBuilder         // ⭐ REQUIRED FOR TODO-3
) : ViewModel() {

    // ---------------------------------------------------------
    // TODO-0: Blank PDF
    // ---------------------------------------------------------
    private val _pdfUri = MutableStateFlow<String?>(null)
    val pdfUri = _pdfUri.asStateFlow()

    // ---------------------------------------------------------
    // TODO-1: Fake-data PDF
    // ---------------------------------------------------------
    private val _todo1PdfUri = MutableStateFlow<String?>(null)
    val todo1PdfUri = _todo1PdfUri.asStateFlow()

    // ---------------------------------------------------------
    // TODO-2: Styled PDF (fake data)
    // ---------------------------------------------------------
    private val _todo2PdfUri = MutableStateFlow<String?>(null)
    val todo2PdfUri = _todo2PdfUri.asStateFlow()

    // ---------------------------------------------------------
    // TODO-3: REAL DB Report
    // ---------------------------------------------------------
    private val _todo3PdfUri = MutableStateFlow<String?>(null)
    val todo3PdfUri = _todo3PdfUri.asStateFlow()

    // ---------------------------------------------------------
    // Auto-open one-time event
    // ---------------------------------------------------------
    private val _openPdfEvent = Channel<String>(Channel.BUFFERED)
    val openPdfEvent = _openPdfEvent.receiveAsFlow()


    // =========================================================
    // TODO-0: Blank PDF
    // =========================================================
    fun runBlankPdfTest() {
        viewModelScope.launch {
            val uri = pdfTestGenerator.createBlankPdfTest().getOrNull()
            _pdfUri.value = uri
            if (uri != null) _openPdfEvent.send(uri)
        }
    }

    // =========================================================
    // TODO-1: Fake Data PDF
    // =========================================================
    fun runTodo1Test() {
        viewModelScope.launch {
            val report = ReportFakeData.generateFakeReport()
            val uri = todo1Generator.generateTestReport(report).getOrNull()
            _todo1PdfUri.value = uri
            if (uri != null) _openPdfEvent.send(uri)
        }
    }

    // =========================================================
    // TODO-2: Modern Styled PDF (Fake Data)
    // =========================================================
    fun runTodo2Test() {
        viewModelScope.launch {
            val report = ReportFakeData.generateFakeReport()
            val uri = todo2Generator.generateReportPdf(report).getOrNull()
            _todo2PdfUri.value = uri
            if (uri != null) _openPdfEvent.send(uri)
        }
    }

    // =========================================================
    // TODO-3: REAL DB DATA → PDF REPORT
    // =========================================================
    fun runTodo3RealReport() {
        viewModelScope.launch {

            // Build REAL report (DB + calculator)
            val report = realReportBuilder.buildReport()

            // Use styled PDF engine (TODO-2 generator) for final output
            val uri = todo2Generator.generateReportPdf(report).getOrNull()

            _todo3PdfUri.value = uri
            if (uri != null) _openPdfEvent.send(uri)
        }
    }
}
