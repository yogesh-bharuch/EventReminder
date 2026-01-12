package com.example.eventreminder.pdf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.logging.SAVE_TAG
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import timber.log.Timber

/**
 * PdfViewModel
 *
 * Caller(s):
 *  - HomeScreen
 *
 * Responsibility:
 *  - Acts as the single orchestration point for all PDF generation flows.
 *  - Coordinates report building and PDF rendering.
 *  - Guards against concurrent / duplicate generation requests.
 *  - Emits one-time UI events for opening generated PDF files.
 *
 * Managed Reports:
 *  - Active Alarm Report (grouped + flat view)
 *  - Reminder List Report (new feature, no offsets)
 *
 * State:
 *  - Exposes generation progress via StateFlow.
 *  - Emits generated PDF Uri via one-time Channel.
 *
 * Side Effects:
 *  - Writes PDF files to public Documents storage.
 *  - Logs generation lifecycle using Timber.
 *
 * Notes:
 *  - Contains NO rendering logic.
 *  - Contains NO database access.
 *  - Delegates all heavy work to builders and PdfGenerator.
 */
@HiltViewModel
class PdfViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val pdfGenerator: PdfGenerator,
    private val realReportBuilder: RealReportBuilder,
    private val reminderListReportBuilder: ReminderListReportBuilder
) : ViewModel() {

    // ---------------------------------------------------------
    // StateFlows
    // ---------------------------------------------------------

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
    /**
     * Caller(s):
     *  - HomeScreen → onGeneratePdfClick()
     *
     * Responsibility:
     *  - Builds the ACTIVE ALARM report using real database data.
     *  - Delegates PDF rendering to PdfGenerator.
     *  - Guards against concurrent / double-tap generation.
     *  - Emits a one-time open-PDF UI event on success.
     *
     * Output:
     *  - Emits Uri via openPdfEvent on successful generation.
     *  - Updates isGeneratingPdf StateFlow for UI loading state.
     *
     * Side Effects:
     *  - Writes a PDF file to public Documents storage.
     *
     * Failure Handling:
     *  - Errors are logged via Timber.
     *  - UI failure message is emitted.
     *  - isGeneratingPdf is always reset.
     */
    fun allAlarmsReport() {
        viewModelScope.launch {
            if (_isGeneratingPdf.value) return@launch   // ⛔ double-tap guard
            _isGeneratingPdf.value = true

            try {
                val report = realReportBuilder.buildReport()
                val uri = pdfGenerator
                    .generateAlarmsReportPdf(appContext, report)
                    .getOrNull()

                if (uri == null) {
                    // ❌ generation failed
                    ReminderViewModel.UiEvent.ShowMessage("PDF generation failed")
                        .also { /* handled by HomeScreen */ }
                    return@launch
                }

                //_todo3PdfUri.value = uri
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

}
