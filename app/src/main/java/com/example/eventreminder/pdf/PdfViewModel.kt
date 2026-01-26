package com.example.eventreminder.pdf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.logging.DEBUG_TAG
import com.example.eventreminder.logging.SAVE_TAG
import com.example.eventreminder.logging.SHARE_PDF_TAG
import com.example.eventreminder.pdf.PdfGenerationCoordinator
import com.example.eventreminder.pdf.PdfGenerationResult
import com.example.eventreminder.pdf.PdfDeliveryMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import com.example.eventreminder.ui.viewmodels.ReminderViewModel

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
 *  - Active Alarm Report (grouped)
 *  - Next 7 Days Reminders Report (flat)
 *  - Contacts PDF (format blueprint / sample)
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
 *  - Delegates all heavy work to builders and PdfRepository.
 */
@HiltViewModel
class PdfViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val pdfGenerator: PdfGenerator,
    private val reminderReportDataBuilder: ReminderReportDataBuilder,
    private val repository: PdfRepository,
    private val next7DaysPdfUseCase: Next7DaysPdfUseCase,
    private val pdfGenerationCoordinator: PdfGenerationCoordinator   // 🔁 unified pipeline
) : ViewModel() {

    // ---------------------------------------------------------
    // State
    // ---------------------------------------------------------
    private val _isWorkingPDF = MutableStateFlow(false)
    val isWorkingPDF: StateFlow<Boolean> = _isWorkingPDF

    // ---------------------------------------------------------
    // One-time open PDF event (UI only)
    // ---------------------------------------------------------
    private val _openPdfEvent = Channel<Uri>(Channel.BUFFERED)
    val openPdfEvent = _openPdfEvent.receiveAsFlow()

    // =========================================================
    // ALL ALARMS REPORT
    // =========================================================
    /**
     * Caller(s):
     *  - HomeScreen → onGeneratePdfClick()
     *
     * Responsibility:
     *  - Builds ACTIVE alarm report using real DB data.
     *  - Delegates rendering to PdfGenerator.
     *  - Emits open-PDF UI event on success.
     *
     * Side Effects:
     *  - Writes PDF to public Documents storage.
     */
    fun allAlarmsReport() {
        viewModelScope.launch {
            if (_isWorkingPDF.value) return@launch
            _isWorkingPDF.value = true

            try {
                Timber.tag(DEBUG_TAG).d("All alarms PDF requested [PdfViewModel.kt::allAlarmsReport]")

                val report = reminderReportDataBuilder.buildActiveAlarmReport()

                Timber.tag(SHARE_PDF_TAG).d("Active alarms count=${report.sortedAlarms.size} generatedAt=${report.generatedAt} " + "[PdfViewModel.kt::allAlarmsReport]")

                val uri = pdfGenerator
                    .generateAlarmsReportPdf(appContext, report)
                    .getOrNull()

                if (uri == null) {
                    ReminderViewModel.UiEvent.ShowMessage("PDF generation failed")
                    return@launch
                }

                _openPdfEvent.send(uri)

                Timber.tag(SAVE_TAG).d("📄 Alarm PDF generated → $uri [PdfViewModel.kt::allAlarmsReport]")

            } catch (e: Exception) {
                Timber.tag(SAVE_TAG).e(e, "💥 Alarm PDF generation error [PdfViewModel.kt::allAlarmsReport]")
            } finally {
                _isWorkingPDF.value = false
            }
        }
    }

    // =========================================================
    // ContactsPdf REPORT
    // =========================================================
    /**
     * Caller(s):
     *  - HomeScreen → bottom tray → Export
     *
     * Responsibility:
     *  - Generates a static contacts PDF.
     *  - Serves as a format and MediaStore blueprint.
     *
     * Side Effects:
     *  - Writes PDF to public Documents storage.
     */
    fun generateContactsPdf() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isWorkingPDF.value) return@launch
            _isWorkingPDF.value = true

            try {
                val headers = listOf("Sr.No", "Name", "Lastname", "Phone")
                val colWidths = listOf(60f, 120f, 120f, 200f)

                val rows = listOf(
                    listOf(
                        PdfCell.TextCell("1"),
                        PdfCell.TextCell("Yogesh"),
                        PdfCell.TextCell("Vyas"),
                        PdfCell.TextCell("9998000000")
                    ),
                    listOf(
                        PdfCell.TextCell("2"),
                        PdfCell.TextCell("Rahul"),
                        PdfCell.TextCell("Sharma"),
                        PdfCell.TextCell("8888000000")
                    )
                )

                val uri = repository.generatePdf(
                    title = "My Contacts",
                    headers = headers,
                    colWidths = colWidths,
                    rows = rows,
                    layout = PdfLayoutConfig(),
                    fileName = "contacts_${System.currentTimeMillis()}.pdf"
                )

                if (uri != null) {
                    _openPdfEvent.send(uri)
                }

            } finally {
                _isWorkingPDF.value = false
            }
        }
    }

    // =========================================================
    // NEXT 7 DAYS REMINDERS → UI ENTRY
    // =========================================================
    /**
     * Caller(s):
     *  - HomeScreen → bottom tray → "Next 7 Days PDF"
     *
     * Responsibility:
     *  - UI-facing wrapper for next-7-days reminders PDF.
     *  - Delegates generation to centralized coordinator.
     *  - Emits open-PDF UI event on success.
     */
    fun generateNext7DaysRemindersPdf() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isWorkingPDF.value) return@launch
            _isWorkingPDF.value = true

            Timber.tag(SHARE_PDF_TAG).d("Next 7 days PDF requested [PdfViewModel.kt::generateNext7DaysRemindersPdf]")

            try {

                // 🧹 UI OVERRIDE: clear ledger so user click always delivers once
                pdfGenerationCoordinator.clearNext7DaysDelivery()

                val result = pdfGenerationCoordinator.generateNext7Days(
                    delivery = PdfDeliveryMode.UI_AND_NOTIFICATION
                )

                when (result) {
                    is PdfGenerationResult.Success -> {
                        _openPdfEvent.send(result.uri)
                        Timber.tag(SAVE_TAG).d("📄 Next 7 days PDF generated → ${result.uri} " + "[PdfViewModel.kt::generateNext7DaysRemindersPdf]")
                    }
                    is PdfGenerationResult.Skipped -> {
                        Timber.tag(SHARE_PDF_TAG).d("Next 7 days PDF skipped reason=${result.reason} " + "[PdfViewModel.kt::generateNext7DaysRemindersPdf]")
                    }
                    is PdfGenerationResult.Failed -> {
                        ReminderViewModel.UiEvent.ShowMessage("PDF generation failed")
                        Timber.tag(SHARE_PDF_TAG).e("Next 7 days PDF failed reason=${result.error} " + "[PdfViewModel.kt::generateNext7DaysRemindersPdf]")
                    }
                }

            } catch (e: Exception) {
                Timber.tag(DEBUG_TAG).e(e, "💥 Next 7 days PDF error [PdfViewModel.kt::generateNext7DaysRemindersPdf]")
            } finally {
                _isWorkingPDF.value = false
            }
        }
    }

    /*
      Headless generator intentionally preserved for historical reference.
      Orchestration is now centralized via PdfGenerationCoordinator.
     */
}
