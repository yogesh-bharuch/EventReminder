package com.example.eventreminder.pdf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.logging.DEBUG_TAG
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
import kotlinx.coroutines.Dispatchers
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
    private val reminderReportDataBuilder: ReminderReportDataBuilder,
    private val repository: PdfRepository
) : ViewModel() {

    // ---------------------------------------------------------
    // StateFlows
    // ---------------------------------------------------------

    private val _isWorkingPDF = MutableStateFlow(false)
    val isWorkingPDF: StateFlow<Boolean> = _isWorkingPDF

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
            // ⛔ double-tap guard
            if (_isWorkingPDF.value) return@launch
            _isWorkingPDF.value = true

            try {
                val report = reminderReportDataBuilder.buildActiveAlarmReport()
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

            }
            catch (e: Exception) {
                Timber.tag(SAVE_TAG).e(e, "💥 Alarm PDF generation error [PdfViewModel.kt::runTodo3RealReport]")
                ReminderViewModel.UiEvent.ShowMessage("PDF generation failed")
                    .also { /* handled by HomeScreen */ }

            }
            finally {
                _isWorkingPDF.value = false   // ✅ ALWAYS reset
            }
        }
    }

    // =========================================================
    // CONTACTS REPORT → PDF
    // this is for formate idiomatic blue print
    // =========================================================
    fun generateContactsPdf() {
        viewModelScope.launch(Dispatchers.IO) {
            // ⛔ double-tap guard
            if (_isWorkingPDF.value) return@launch
            _isWorkingPDF.value = true

            Timber.tag(DEBUG_TAG).d("viewmodel called. [PdfViewModel.kt::generateContactsPdf]")

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
                val layout = PdfLayoutConfig(
                    pageWidth = 595,
                    pageHeight = 842,
                    margin = 50f,
                    titleSpacing = 30f,
                    afterTitleSpacing = 50f,
                    afterHeaderSpacing = 35f,
                    rowSpacing = 25f,
                    footerBreathing = 30f
                )
                val uri = repository.generatePdf(
                    title = "My Contacts",
                    headers = headers,
                    colWidths = colWidths,
                    rows = rows,
                    layout = layout,
                    fileName = "contacts.pdf"
                )

                if (uri == null) {
                    ReminderViewModel.UiEvent.ShowMessage("PDF generation failed")
                        .also { /* handled by HomeScreen */ }
                    return@launch
                }

                Timber.tag(DEBUG_TAG).d("$uri called from try block. [PdfViewModel.kt::generateContactsPdf]")
                _openPdfEvent.send(uri)

                Timber.tag(SAVE_TAG).d("📄 Contacts PDF generated → $uri [PdfViewModel.kt::generateContactsPdf]")

                ReminderViewModel.UiEvent.ShowMessage("PDF generated successfully")
                    .also { /* handled by HomeScreen */ }

            }
            catch (e: Exception) {
                Timber.tag(SAVE_TAG).e(e, "💥 Contacts PDF generation error [PdfViewModel.kt::generateContactsPdf]")
                ReminderViewModel.UiEvent.ShowMessage("PDF generation failed")
                    .also { /* handled by HomeScreen */ }
            }
            finally {
                _isWorkingPDF.value = false   // ✅ ALWAYS reset
            }
        }
    }

    // =========================================================
    // NEXT 7 DAYS REMINDERS → PDF (FLAT LIST)
    // =========================================================
    /**
     * Caller(s):
     *  - HomeScreen → bottom tray → "Next 7 Days PDF"
     *
     * Responsibility:
     *  - Builds a flat list of reminders occurring in the next 7 days.
     *  - Sorts reminders by trigger time (ascending).
     *  - Converts reminders into generic PDF table rows.
     *  - Delegates rendering + storage to PdfRepository.
     *  - Emits a one-time open-PDF UI event.
     *
     * Notes:
     *  - No grouping is applied.
     *  - Uses generic PDF renderer.
     */
    fun generateNext7DaysRemindersPdf() {
        viewModelScope.launch(Dispatchers.IO) {
            // ⛔ double-tap guard
            if (_isWorkingPDF.value) return@launch
            _isWorkingPDF.value = true

            Timber.tag(DEBUG_TAG).d("Next 7 days reminders PDF requested. [PdfViewModel.kt::generateNext7DaysRemindersPdf]")

            try {
                // ---------------------------------------------------------
                // 1. Fetch reminders for next 7 days (already sorted by builder)
                // ---------------------------------------------------------
                val reminders = reminderReportDataBuilder.buildNext7DaysReminders()

                if (reminders.isEmpty()) {
                    ReminderViewModel.UiEvent.ShowMessage("No reminders in next 7 days")
                        .also { /* handled by HomeScreen */ }
                    return@launch
                }

                // ---------------------------------------------------------
                // 2. Define table structure
                // ---------------------------------------------------------
                val headers = listOf("Description", "Trigger Time", "Offset")
                val colWidths = listOf(220f, 200f, 100f)

                // ---------------------------------------------------------
                // 3. Map AlarmEntry → PDF rows (Today + Upcoming sections)
                // ---------------------------------------------------------
                val dateFormatter =
                    java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

                val zoneId = java.time.ZoneId.systemDefault()
                val today = java.time.LocalDate.now(zoneId)

                val (todayReminders, upcomingReminders) = reminders.partition { alarm ->
                    toLocalDateTime(alarm.nextTrigger, zoneId).toLocalDate() == today
                }

                val rows = buildList {

                    // -------------------------------
                    // TODAY SECTION
                    // -------------------------------
                    if (todayReminders.isEmpty())
                    {
                        add(
                            listOf(
                                PdfCell.TextCell("Today  -  No Reminder"),
                                PdfCell.TextCell(" "),
                                PdfCell.TextCell(" ")
                            )
                        )
                    } else {
                        add(
                            listOf(
                                PdfCell.TextCell("Today"),
                                PdfCell.TextCell(" "),
                                PdfCell.TextCell(" ")
                            )
                        )

                        todayReminders.forEach { alarm ->
                            val localDateTime = toLocalDateTime(alarm.nextTrigger, zoneId)
                            val descriptionText = "${pickEventEmoji(alarm.description ?: "")} ${alarm.description ?: "-"}"

                            add(
                                listOf(
                                    PdfCell.TextCell(descriptionText),
                                    PdfCell.TextCell(localDateTime.format(dateFormatter)),
                                    PdfCell.TextCell(formatOffsetText(alarm.offsetMinutes))
                                )
                            )
                        }
                    }

                    // -------------------------------
                    // UPCOMING SECTION
                    // -------------------------------
                    if (upcomingReminders.isEmpty())
                    {
                        add(
                            listOf(
                                PdfCell.TextCell("Upcoming  -  No Reminder"),
                                PdfCell.TextCell(" "),
                                PdfCell.TextCell(" ")
                            )
                        )
                    } else {
                        add(
                            listOf(
                                PdfCell.TextCell("Upcoming"),
                                PdfCell.TextCell(" "),
                                PdfCell.TextCell(" ")
                            )
                        )

                        upcomingReminders.forEach { alarm ->
                            val localDateTime = toLocalDateTime(alarm.nextTrigger, zoneId)
                            val descriptionText = "${pickEventEmoji(alarm.description ?: "")} ${alarm.description ?: "-"}"

                            add(
                                listOf(
                                    PdfCell.TextCell(descriptionText),
                                    PdfCell.TextCell(localDateTime.format(dateFormatter)),
                                    PdfCell.TextCell(formatOffsetText(alarm.offsetMinutes))
                                )
                            )
                        }
                    }
                }

                // ---------------------------------------------------------
                // 4. Layout configuration
                // ---------------------------------------------------------
                val layout = PdfLayoutConfig(
                    pageWidth = 595,
                    pageHeight = 842,
                    margin = 50f,
                    titleSpacing = 30f,
                    afterTitleSpacing = 40f,
                    afterHeaderSpacing = 30f,
                    rowSpacing = 25f,
                    footerBreathing = 30f
                )

                // ---------------------------------------------------------
                // 5. Generate PDF
                // ---------------------------------------------------------
                val uri = repository.generatePdf(
                    title = "Reminders – Next 7 Days",
                    headers = headers,
                    colWidths = colWidths,
                    rows = rows,
                    layout = layout,
                    fileName = "Reminders_Next_7_Days_${System.currentTimeMillis()}.pdf"
                )

                if (uri == null) {
                    ReminderViewModel.UiEvent.ShowMessage("PDF generation failed")
                        .also { /* handled by HomeScreen */ }
                    return@launch
                }

                // ---------------------------------------------------------
                // 6. Open PDF
                // ---------------------------------------------------------
                _openPdfEvent.send(uri)

                Timber.tag(DEBUG_TAG).d("📄 Next 7 days reminders PDF generated → $uri [PdfViewModel.kt::generateNext7DaysRemindersPdf]")

                ReminderViewModel.UiEvent.ShowMessage("PDF generated successfully")
                    .also { /* handled by HomeScreen */ }

            }
            catch (e: Exception) {
                Timber.tag(DEBUG_TAG).e(e, "💥 Next 7 days reminders PDF error [PdfViewModel.kt::generateNext7DaysRemindersPdf]")

                ReminderViewModel.UiEvent.ShowMessage("PDF generation failed")
                    .also { /* handled by HomeScreen */ }

            }
            finally {
                _isWorkingPDF.value = false
            }
        }
    }

    // Helpers
    /**
     * Converts epoch millis to LocalDateTime using the given ZoneId.
     */
    fun toLocalDateTime(triggerEpochMillis: Long, zoneId: java.time.ZoneId): java.time.LocalDateTime =
        java.time.Instant.ofEpochMilli(triggerEpochMillis)
            .atZone(zoneId)
            .toLocalDateTime()

    /**
     * Formats offset minutes into a human-readable label.
     */
    fun formatOffsetText(offsetMinutes: Long): String =
        when {
            offsetMinutes <= 0L -> "on time"
            offsetMinutes % (24 * 60) == 0L ->
                "${offsetMinutes / (24 * 60)} day before"
            offsetMinutes % 60 == 0L ->
                "${offsetMinutes / 60} hr before"
            else ->
                "${offsetMinutes} min before"
        }

    private val eventEmojiMap: Map<String, String> = mapOf(
        "medicine" to "💊",
        "money" to "💰",
        "travel" to "✈️",
        "home" to "🏠",
        "celebration" to "🎉",
        "time" to "⏰",
        "default" to "🔔"
    )

    private fun pickEventEmoji(title: String): String {
        val t = title.lowercase()
        return when {
            listOf("medicine", "pill", "tablet", "dose").any { t.contains(it) } ->
                eventEmojiMap["medicine"]!!
            listOf("pay", "rent", "emi", "bank", "bill", "payment", "renewal").any { t.contains(it) } ->
                eventEmojiMap["money"]!!
            listOf("flight", "trip", "travel", "airport").any { t.contains(it) } ->
                eventEmojiMap["travel"]!!
            listOf("plant", "plants", "water", "garden").any { t.contains(it) } ->
                eventEmojiMap["home"]!!
            listOf("birthday", "party", "anniversary", "celebration").any { t.contains(it) } ->
                eventEmojiMap["celebration"]!!
            listOf("debug", "test").any { t.contains(it) } ->
                eventEmojiMap["time"]!!
            else -> eventEmojiMap["default"]!!
        }

    }

}
