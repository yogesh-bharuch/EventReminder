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

@HiltViewModel
class PdfViewModel @Inject constructor(
    private val pdfTestGenerator: PdfTestGenerator,
    private val todo1Generator: PdfTodo1Generator,
    private val todo2Generator: PdfTodo2Generator      // ⭐ REQUIRED FOR TODO-2
) : ViewModel() {

    // TODO-0
    private val _pdfUri = MutableStateFlow<String?>(null)
    val pdfUri = _pdfUri.asStateFlow()

    // TODO-1
    private val _todo1PdfUri = MutableStateFlow<String?>(null)
    val todo1PdfUri = _todo1PdfUri.asStateFlow()

    // TODO-2  ⭐ NEW
    private val _todo2PdfUri = MutableStateFlow<String?>(null)
    val todo2PdfUri = _todo2PdfUri.asStateFlow()

    // Auto-open one-time event
    private val _openPdfEvent = Channel<String>(Channel.BUFFERED)
    val openPdfEvent = _openPdfEvent.receiveAsFlow()


    // =========================================================
    // TODO-0
    // =========================================================
    fun runBlankPdfTest() {
        viewModelScope.launch {
            val uri = pdfTestGenerator.createBlankPdfTest().getOrNull()
            _pdfUri.value = uri
            if (uri != null) _openPdfEvent.send(uri)
        }
    }

    // =========================================================
    // TODO-1
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
    // TODO-2  ⭐ NEW
    // =========================================================
    fun runTodo2Test() {
        viewModelScope.launch {
            val report = ReportFakeData.generateFakeReport()
            val uri = todo2Generator.generateReportPdf(report).getOrNull()
            _todo2PdfUri.value = uri
            if (uri != null) _openPdfEvent.send(uri)
        }
    }
}
