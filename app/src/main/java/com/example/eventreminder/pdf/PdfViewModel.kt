package com.example.eventreminder.pdf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.logging.DEBUG_TAG
import com.example.eventreminder.logging.SAVE_TAG
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
    private val repository: PdfRepository
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
                Timber.tag(DEBUG_TAG)
                    .d("All alarms PDF requested [PdfViewModel.kt::allAlarmsReport]")

                val report = reminderReportDataBuilder.buildActiveAlarmReport()

                Timber.tag(DEBUG_TAG)
                    .d(
                        "Active alarms count=${report.sortedAlarms.size} generatedAt=${report.generatedAt} " +
                                "[PdfViewModel.kt::allAlarmsReport]"
                    )

                val uri = pdfGenerator
                    .generateAlarmsReportPdf(appContext, report)
                    .getOrNull()

                if (uri == null) {
                    ReminderViewModel.UiEvent.ShowMessage("PDF generation failed")
                    return@launch
                }

                _openPdfEvent.send(uri)

                Timber.tag(SAVE_TAG)
                    .d("📄 Alarm PDF generated → $uri [PdfViewModel.kt::allAlarmsReport]")

            } catch (e: Exception) {
                Timber.tag(SAVE_TAG)
                    .e(e, "💥 Alarm PDF generation error [PdfViewModel.kt::allAlarmsReport]")
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
                    fileName = "contacts.pdf"
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
     *  - Delegates headless generation to internal function.
     *  - Emits open-PDF UI event on success.
     */
    fun generateNext7DaysRemindersPdf() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isWorkingPDF.value) return@launch
            _isWorkingPDF.value = true

            Timber.tag(DEBUG_TAG)
                .d("Next 7 days PDF requested [PdfViewModel.kt::generateNext7DaysRemindersPdf]")

            try {
                val uri = generateNext7DaysRemindersPdfInternal()

                if (uri == null) {
                    ReminderViewModel.UiEvent.ShowMessage("PDF generation failed")
                    return@launch
                }

                _openPdfEvent.send(uri)

                Timber.tag(SAVE_TAG)
                    .d("📄 Next 7 days PDF generated → $uri [PdfViewModel.kt::generateNext7DaysRemindersPdf]")

            } catch (e: Exception) {
                Timber.tag(DEBUG_TAG)
                    .e(e, "💥 Next 7 days PDF error [PdfViewModel.kt::generateNext7DaysRemindersPdf]")
            } finally {
                _isWorkingPDF.value = false
            }
        }
    }

    // =========================================================
    // NEXT 7 DAYS REMINDERS → HEADLESS GENERATOR
    // =========================================================
    /**
     * Caller(s):
     *  - generateNext7DaysRemindersPdf()
     *  - Background Worker (daily automation)
     *
     * Responsibility:
     *  - Generates Next 7 Days reminders PDF without UI side effects.
     *  - SAFE for background / Worker execution.
     *
     * Output:
     *  - Uri of generated PDF, or null on failure.
     */
    private suspend fun generateNext7DaysRemindersPdfInternal(): Uri? {

        val reminders = reminderReportDataBuilder.buildNext7DaysReminders()
        val zoneId = ZoneId.systemDefault()

        Timber.tag(DEBUG_TAG)
            .d(
                "Next7Days reminders loaded count=${reminders.size} now=${Instant.now().atZone(zoneId)} " +
                        "[PdfViewModel.kt::generateNext7DaysRemindersPdfInternal]"
            )

        val headers = listOf("Description", "Trigger Time", "Offset")
        val colWidths = listOf(220f, 200f, 100f)

        val formatter =
            java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

        val (todayReminders, upcomingReminders) =
            reminders.partition { isToday(it.nextTrigger, zoneId) }

        Timber.tag(DEBUG_TAG)
            .d(
                "Partitioned reminders today=${todayReminders.size} upcoming=${upcomingReminders.size} " +
                        "[PdfViewModel.kt::generateNext7DaysRemindersPdfInternal]"
            )

        val rows = buildList {

            // TODAY
            if (todayReminders.isEmpty()) {
                add(listOf(PdfCell.TextCell("Today  -  No Reminder"), PdfCell.TextCell(" "), PdfCell.TextCell(" ")))
            } else {
                add(listOf(PdfCell.TextCell("Today"), PdfCell.TextCell(" "), PdfCell.TextCell(" ")))
                todayReminders.forEach {
                    add(
                        listOf(
                            PdfCell.TextCell("${pickEventEmoji(it.description ?: "")} ${it.description ?: "-"}"),
                            PdfCell.TextCell(toLocalDateTime(it.nextTrigger, zoneId).format(formatter)),
                            PdfCell.TextCell(formatOffsetText(it.offsetMinutes))
                        )
                    )
                }
            }

            // UPCOMING
            if (upcomingReminders.isEmpty()) {
                add(listOf(PdfCell.TextCell("Upcoming  -  No Reminder"), PdfCell.TextCell(" "), PdfCell.TextCell(" ")))
            } else {
                add(listOf(PdfCell.TextCell("Upcoming"), PdfCell.TextCell(" "), PdfCell.TextCell(" ")))
                upcomingReminders.forEach {
                    add(
                        listOf(
                            PdfCell.TextCell("${pickEventEmoji(it.description ?: "")} ${it.description ?: "-"}"),
                            PdfCell.TextCell(toLocalDateTime(it.nextTrigger, zoneId).format(formatter)),
                            PdfCell.TextCell(formatOffsetText(it.offsetMinutes))
                        )
                    )
                }
            }
        }

        return repository.generatePdf(
            title = "Reminders – Next 7 Days",
            headers = headers,
            colWidths = colWidths,
            rows = rows,
            layout = PdfLayoutConfig(),
            fileName = "Reminders_Next_7_Days.pdf"
        )
    }

    // =========================================================
    // Helpers
    // =========================================================
    private fun toLocalDateTime(epochMillis: Long, zoneId: ZoneId) =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDateTime()

    private fun isToday(epochMillis: Long, zoneId: ZoneId): Boolean =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate() ==
                java.time.LocalDate.now(zoneId)

    private fun formatOffsetText(offsetMinutes: Long): String =
        when {
            offsetMinutes <= 0L -> "on time"
            offsetMinutes % (24 * 60) == 0L -> "${offsetMinutes / (24 * 60)} day before"
            offsetMinutes % 60 == 0L -> "${offsetMinutes / 60} hr before"
            else -> "${offsetMinutes} min before"
        }

    private val eventEmojiMap = mapOf(
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
            listOf("medicine", "pill", "tablet", "dose").any { t.contains(it) } -> eventEmojiMap["medicine"]!!
            listOf("pay", "rent", "emi", "bank", "bill", "payment", "renewal").any { t.contains(it) } -> eventEmojiMap["money"]!!
            listOf("flight", "trip", "travel", "airport").any { t.contains(it) } -> eventEmojiMap["travel"]!!
            listOf("plant", "plants", "water", "garden").any { t.contains(it) } -> eventEmojiMap["home"]!!
            listOf("birthday", "party", "anniversary", "celebration").any { t.contains(it) } -> eventEmojiMap["celebration"]!!
            listOf("debug", "test").any { t.contains(it) } -> eventEmojiMap["time"]!!
            else -> eventEmojiMap["default"]!!
        }
    }
}
