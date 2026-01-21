package com.example.eventreminder.pdf

import com.example.eventreminder.logging.DEBUG_TAG
import com.example.eventreminder.logging.SHARE_PDF_TAG
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Next7DaysPdfUseCase
 *
 * Caller(s):
 *  - PdfViewModel (UI flow)
 *  - Next7DaysPdfWorker (background automation)
 *
 * Responsibility:
 *  - Single source of truth for "Next 7 Days Reminders" PDF generation.
 *  - Builds grouped rows (Today / Upcoming / Empty states).
 *  - Delegates PDF rendering & storage to PdfRepository.
 *
 * Guarantees:
 *  - Identical output for UI-triggered and Worker-triggered PDFs.
 *  - NO UI side effects.
 *  - SAFE for background execution.
 *
 * Return:
 *  - Uri of generated PDF, or null on failure.
 */
@Singleton
class Next7DaysPdfUseCase @Inject constructor(
    private val reminderReportDataBuilder: ReminderReportDataBuilder,
    private val repository: PdfRepository
) {

    /**
     * Generates "Next 7 Days Reminders" PDF.
     *
     * Caller(s):
     *  - PdfViewModel
     *  - WorkManager Worker
     *
     * Return:
     *  - Uri of generated PDF, or null if generation failed.
     */
    suspend fun generate(): android.net.Uri? {

        val zoneId = ZoneId.systemDefault()
        val now = Instant.now()
        val formatter = DateTimeFormatter.ofPattern("dd MMM, yyyy 'T:' HH:mm:ss z").withZone(zoneId)

        Timber.tag(SHARE_PDF_TAG).d("Next7DaysPdfUseCase START now=${formatter.format(now)} " + "[Next7DaysPdfUseCase.kt::generate]")

        val reminders = reminderReportDataBuilder.buildNext7DaysReminders()

        Timber.tag(SHARE_PDF_TAG).d("Reminders loaded count=${reminders.size} " + "[Next7DaysPdfUseCase.kt::generate]")

        val (todayReminders, upcomingReminders) =
            reminders.partition { isToday(it.nextTrigger, zoneId) }

        Timber.tag(SHARE_PDF_TAG).d("Partitioned reminders today=${todayReminders.size} upcoming=${upcomingReminders.size} " + "[Next7DaysPdfUseCase.kt::generate]")

        //val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

        val rows = buildList {

            // -------------------------------------------------
            // TODAY SECTION
            // -------------------------------------------------
            if (todayReminders.isEmpty()) {
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
                    add(
                        listOf(
                            PdfCell.TextCell("${pickEventEmoji(alarm.description ?: "")} ${alarm.description ?: "-"}"),
                            PdfCell.TextCell(
                                Instant.ofEpochMilli(alarm.nextTrigger)
                                    .atZone(zoneId)
                                    .format(formatter)
                            ),
                            PdfCell.TextCell(formatOffsetText(alarm.offsetMinutes))
                        )
                    )
                }
            }

            // -------------------------------------------------
            // UPCOMING SECTION
            // -------------------------------------------------
            if (upcomingReminders.isEmpty()) {
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
                    add(
                        listOf(
                            PdfCell.TextCell("${pickEventEmoji(alarm.description ?: "")} ${alarm.description ?: "-"}"),
                            PdfCell.TextCell(
                                Instant.ofEpochMilli(alarm.nextTrigger)
                                    .atZone(zoneId)
                                    .format(formatter)
                            ),
                            PdfCell.TextCell(formatOffsetText(alarm.offsetMinutes))
                        )
                    )
                }
            }
        }

        val uri = repository.generatePdf(
            title = "Reminders – Next 7 Days",
            headers = listOf("Description", "Trigger Time", "Offset"),
            colWidths = listOf(220f, 200f, 100f),
            rows = rows,
            layout = PdfLayoutConfig(),
            fileName = "Reminders_Next_7_Days.pdf"
        )

        Timber.tag(SHARE_PDF_TAG).d("Next7DaysPdfUseCase END uri=$uri " + "[Next7DaysPdfUseCase.kt::generate]")

        return uri
    }

    // =========================================================
    // Helpers
    // =========================================================

    private fun isToday(epochMillis: Long, zoneId: ZoneId): Boolean =
        Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .toLocalDate() == java.time.LocalDate.now(zoneId)

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
            else ->
                eventEmojiMap["default"]!!
        }
    }
}
