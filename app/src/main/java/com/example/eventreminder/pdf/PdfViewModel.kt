package com.example.eventreminder.pdf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.eventreminder.ui.viewmodels.ReminderViewModel



@HiltViewModel
class PdfViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val pdfGenerator: PdfGenerator,
    private val realReportBuilder: RealReportBuilder,
    private val reminderListReportBuilder: ReminderListReportBuilder
) : ViewModel() {

    // ---------------------------------------------------------
    // StateFlows for each PDF
    // ---------------------------------------------------------
    private val _pdfUri = MutableStateFlow<Uri?>(null)
    val pdfUri = _pdfUri.asStateFlow()

    private val _todo3PdfUri = MutableStateFlow<Uri?>(null)
    val todo3PdfUri = _todo3PdfUri.asStateFlow()

    private val _isGeneratingPdf = MutableStateFlow(false)
    val isGeneratingPdf: StateFlow<Boolean> = _isGeneratingPdf

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
            if (_isGeneratingPdf.value) return@launch   // ⛔ double-tap guard
            _isGeneratingPdf.value = true

            try {
                val report = realReportBuilder.buildReport()
                val uri = pdfGenerator
                    .generateReportPdf(appContext, report)
                    .getOrNull()

                if (uri == null) {
                    // ❌ generation failed
                    ReminderViewModel.UiEvent.ShowMessage("PDF generation failed")
                        .also { /* handled by HomeScreen */ }
                    return@launch
                }

                _todo3PdfUri.value = uri
                _openPdfEvent.send(uri)

                Timber.tag(SAVE_TAG).d("📄 Alarm PDF generated → $uri [PdfViewModel.kt::runTodo3RealReport]")

                // ✅ success feedback
                ReminderViewModel.UiEvent.ShowMessage("PDF generated successfully")
                    .also { /* handled by HomeScreen */ }

            } catch (e: Exception) {
                Timber.tag(SAVE_TAG).e(e, "💥 Alarm PDF generation error [PdfViewModel.kt::runTodo3RealReport]")
                ReminderViewModel.UiEvent.ShowMessage("PDF generation failed")
                    .also { /* handled by HomeScreen */ }

            } finally {
                _isGeneratingPdf.value = false   // ✅ ALWAYS reset
            }
        }
    }

    // =========================================================
    // REMINDER LIST PDF (NEW FEATURE – NO OFFSETS)
    // =========================================================
    fun runReminderListReport() {
        viewModelScope.launch {
            if (_isGeneratingPdf.value) return@launch
            _isGeneratingPdf.value = true

            try {
                val report = reminderListReportBuilder.buildReport()
                val uri = pdfGenerator
                    .generateReminderListPdf(appContext, report)
                    .getOrNull()

                if (uri == null) {
                    ReminderViewModel.UiEvent.ShowMessage("Export failed")
                        .also { /* handled by HomeScreen */ }
                    return@launch
                }

                _openPdfEvent.send(uri)

                Timber.tag(SAVE_TAG).d("📄 Reminder list PDF generated → $uri [PdfViewModel.kt::runReminderListReport]")

            } catch (e: Exception) {
                Timber.tag(SAVE_TAG).e(e, "💥 Reminder list PDF error [PdfViewModel.kt::runReminderListReport]")
                ReminderViewModel.UiEvent.ShowMessage("Export failed")
                    .also { /* handled by HomeScreen */ }

            } finally {
                _isGeneratingPdf.value = false
            }
        }
    }
}
