package com.example.eventreminder.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.max

/**
 * =============================================================
 * PdfTodo2Generator — FINAL (group header icon = first event's emoji)
 *
 * - Medium spacing (1.4x row height)
 * - Page-1 columns: Event Date & Time | Trigger Time | Offset
 * - Page-2 columns: Event (icon + title) | Event Date & Time | Trigger Time | Offset
 * - Emoji icons auto-detected (keyword heuristics)
 * - Emoji size = 20pt
 * - Pagination automatic; summary only on final page
 * =============================================================
 */
class PdfTodo2Generator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Page geometry (A4-like)
    private val pageWidth = 595
    private val pageHeight = 842

    // Outer margins & breathing
    private val margin = 44f                 // increased outer margin
    private val headerExtra = 20f            // extra breathing below header
    private val footerExtra = 22f            // extra breathing above footer

    // Typography sizes (points)
    private val titleSize = 16f              // report title
    private val groupHeaderSize = 15f        // group header (bold)
    private val bodySize = 13f               // row font
    private val smallSize = 11f               // footer / small text

    // Emoji icon size (your selection B)
    private val iconSize = 20f

    // Row band heights — medium spacing (1.4x)
    private val rowBandHeight = bodySize * 1.4f + 12f
    private val groupHeaderBand = groupHeaderSize + 12f

    private val headerLineColor = Color.argb(28, 0, 0, 0)
    private val separatorColor = Color.argb(70, 200, 200, 200)

    private val headerDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val alarmDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

    // Emoji mappings (default heuristics)
    private val groupEmojiMap = mapOf(
        "Daily Routine" to "🏡",
        "Health" to "💊",
        "Food" to "🍲",
        "Travel" to "✈️",
        "Finance" to "💰",
        "Work" to "💼",
        "Other" to "📌"
    )

    private val eventEmojiMap = mapOf(
        "default" to "📅",
        "medicine" to "💊",
        "money" to "💰",
        "travel" to "✈️",
        "home" to "🏠",
        "celebration" to "🎉",
        "time" to "🕒"
    )

    // =============================================================
    // Public - generate report
    // =============================================================
    fun generateReportPdf(report: ActiveAlarmReport): Result<String> {
        return try {
            val docs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val folder = File(docs, "ReminderReports")
            if (!folder.exists()) folder.mkdirs()

            val file = File(folder, "report_todo2_${System.currentTimeMillis()}.pdf")
            val pdf = PdfDocument()

            // simulate pages to compute totalPages for footer
            val (groupedPagesCount, flatPagesCount) = simulatePageCounts(report)
            val totalPages = groupedPagesCount + flatPagesCount

            var pageNumber = 1
            pageNumber = renderGroupedPages(pdf, report, pageNumber, groupedPagesCount, totalPages)
            pageNumber = renderFlatPages(pdf, report, pageNumber, flatPagesCount, totalPages)

            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()

            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =============================================================
    // Simulation (how many pages needed)
    // =============================================================
    private fun simulatePageCounts(report: ActiveAlarmReport): Pair<Int, Int> {
        val contentTop = margin + titleSize + headerExtra + (bodySize + 14f) // includes space for first column header
        val contentBottom = pageHeight - margin - footerExtra - smallSize
        val usableHeight = contentBottom - contentTop
        val rowsPerPage = max(1, (usableHeight / rowBandHeight).toInt())

        // grouped rows: group header (1) + number of alarms
        var groupedRowCount = 0
        for (group in report.groupedByTitle) {
            if (group.alarms.isEmpty()) continue
            groupedRowCount += 1 // group header
            groupedRowCount += group.alarms.count { it.eventTitle.isNotBlank() && it.nextTrigger > 0L }
        }
        val groupedPages = max(1, (groupedRowCount + rowsPerPage - 1) / rowsPerPage)

        // flat rows: number of alarms
        val flatRowCount = report.sortedAlarms.count { it.eventTitle.isNotBlank() && it.nextTrigger > 0L }
        val flatPages = max(1, (flatRowCount + rowsPerPage - 1) / rowsPerPage)

        return Pair(groupedPages, flatPages)
    }

    // =============================================================
    // Render grouped pages
    // =============================================================
    private fun renderGroupedPages(
        pdf: PdfDocument,
        report: ActiveAlarmReport,
        startPageNumber: Int,
        pagesCount: Int,
        totalPages: Int
    ): Int {
        if (report.groupedByTitle.isEmpty()) return startPageNumber

        var pageNumber = startPageNumber
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        val generated = headerDateFormatter.format(report.generatedAt)
        val headerTitle = "Event Reminder Report — ${context.getStringResourceOrDefault("app_name", "Event Reminder")}"

        drawHeader(canvas, headerTitle, generated)
        var y = margin + titleSize + headerExtra
        y = drawColumnHeaderGrouped(canvas, y) // Event Date | Trigger Time | Offset

        val sectionPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = groupHeaderSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val bodyPaint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = bodySize
        }

        val sepPaint = Paint().apply {
            color = separatorColor
            strokeWidth = 1f
        }

        val contentBottomLimit = pageHeight - margin - footerExtra - smallSize

        for (group in report.groupedByTitle) {
            if (group.alarms.isEmpty()) continue

            // start group header (emoji chosen from FIRST event in the group)
            val firstEmoji = if (group.alarms.isNotEmpty()) pickEventEmoji(group.alarms.first().eventTitle) else (groupEmojiMap["Other"] ?: "📌")
            val headerText = "$firstEmoji  ${group.title}"

            if (y + groupHeaderBand > contentBottomLimit) {
                drawFooter(canvas, pageNumber, totalPages, generated)
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                drawHeader(canvas, headerTitle, generated)
                y = margin + titleSize + headerExtra
                y = drawColumnHeaderGrouped(canvas, y)
            }

            // group header (positioned with band)
            val headerBaseline = computeBaselineForRow(sectionPaint, y, groupHeaderBand)
            canvas.drawText(headerText, margin, headerBaseline, sectionPaint)
            y += groupHeaderBand

            // events
            val sorted = group.alarms.sortedBy { it.nextTrigger }
            for (alarm in sorted) {
                if (alarm.eventTitle.isBlank()) continue
                if (alarm.nextTrigger <= 0L) continue

                if (y + rowBandHeight > contentBottomLimit) {
                    drawFooter(canvas, pageNumber, totalPages, generated)
                    pdf.finishPage(page)
                    pageNumber++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                    canvas = page.canvas
                    drawHeader(canvas, headerTitle, generated)
                    y = margin + titleSize + headerExtra
                    y = drawColumnHeaderGrouped(canvas, y)
                }

                val baseline = computeBaselineForRow(bodyPaint, y, rowBandHeight)

                // Column 1: Event Date & Time (actual event time)
                val eventDateEpoch = alarm.eventDateEpoch
                val eventDateText = formatDate(eventDateEpoch)
                canvas.drawText(eventDateText, margin + 8f, baseline, bodyPaint)

                // Column 2: Trigger Time
                val triggerText = formatDate(alarm.nextTrigger)
                val triggerX = margin + 200f // approximate column x for trigger
                canvas.drawText(triggerText, triggerX, baseline, bodyPaint)

                // Column 3: Offset (right aligned) - ensure long type
                val offsetText = formatOffset(alarm.offsetMinutes.toLong())
                val offsetX = pageWidth - margin - bodyPaint.measureText(offsetText)
                canvas.drawText(offsetText, offsetX, baseline, bodyPaint)

                y += rowBandHeight
            }

            // group separator
            canvas.drawLine(margin, y + 2f, pageWidth - margin, y + 2f, sepPaint)
            y += 12f
        }

        // finish grouped page
        drawFooter(canvas, pageNumber, totalPages, generated)
        pdf.finishPage(page)
        return pageNumber + 1
    }

    // =============================================================
    // Render flat pages (Event + EventDate + Trigger + Offset)
    // =============================================================
    private fun renderFlatPages(
        pdf: PdfDocument,
        report: ActiveAlarmReport,
        startPageNumber: Int,
        pagesCount: Int,
        totalPages: Int
    ): Int {
        var pageNumber = startPageNumber
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        val generated = headerDateFormatter.format(report.generatedAt)
        val headerTitle = "Event Reminder Report — ${context.getStringResourceOrDefault("app_name", "Event Reminder")}"

        drawHeader(canvas, headerTitle, generated)
        var y = margin + titleSize + headerExtra
        y = drawColumnHeaderFlat(canvas, y) // Event | EventDate | Trigger | Offset

        val bodyPaint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = bodySize
        }
        val sepPaint = Paint().apply {
            color = separatorColor
            strokeWidth = 1f
        }

        val contentBottomLimit = pageHeight - margin - footerExtra - smallSize
        for (alarm in report.sortedAlarms) {
            if (alarm.eventTitle.isBlank()) continue
            if (alarm.nextTrigger <= 0L) continue

            if (y + rowBandHeight > contentBottomLimit) {
                // if final page, still may need to draw summary there -> we handle final page after loop
                drawFooter(canvas, pageNumber, totalPages, generated)
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                drawHeader(canvas, headerTitle, generated)
                y = margin + titleSize + headerExtra
                y = drawColumnHeaderFlat(canvas, y)
            }

            val baseline = computeBaselineForRow(bodyPaint, y, rowBandHeight)

            // Column 1: Event (emoji + title)
            val emoji = pickEventEmoji(alarm.eventTitle)
            val emojiPaint = Paint().apply {
                isAntiAlias = true
                color = Color.DKGRAY
                textSize = iconSize
            }
            val iconX = margin + 6f
            canvas.drawText(emoji, iconX, baseline, emojiPaint)
            val titleX = iconX + emojiPaint.measureText(emoji) + 6f
            canvas.drawText(alarm.eventTitle, titleX, baseline, bodyPaint)

            // Column 2: Event Date & Time (actual event)
            val eventDateEpoch = alarm.eventDateEpoch
            val eventDateText = formatDate(eventDateEpoch)
            val eventDateX = margin + 220f
            canvas.drawText(eventDateText, eventDateX, baseline, bodyPaint)

            // Column 3: Trigger Time
            val triggerText = formatDate(alarm.nextTrigger)
            val triggerX = margin + 360f
            canvas.drawText(triggerText, triggerX, baseline, bodyPaint)

            // Column 4: Offset (right aligned)
            val offsetText = formatOffset(alarm.offsetMinutes.toLong())
            val offsetX = pageWidth - margin - bodyPaint.measureText(offsetText)
            canvas.drawText(offsetText, offsetX, baseline, bodyPaint)

            y += rowBandHeight
            canvas.drawLine(margin, y + 2f, pageWidth - margin, y + 2f, sepPaint)
            y += 6f
        }

        // If this page is the final page (pageNumber == totalPages), draw summary above footer.
        val finalPageIsThis = (pageNumber == totalPages)
        if (finalPageIsThis) {
            val summaryTop = pageHeight - margin - footerExtra - 140f
            drawSummaryBox(canvas, summaryTop, report)
        }

        drawFooter(canvas, pageNumber, totalPages, generated)
        pdf.finishPage(page)
        return pageNumber + 1
    }

    // =============================================================
    // Column header drawing helpers
    // =============================================================
    private fun drawColumnHeaderGrouped(canvas: Canvas, startY: Float): Float {
        // Columns: Event Date | Trigger Time | Offset
        val bg = Paint().apply { color = Color.rgb(245, 245, 245) }
        val text = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = bodySize
        }

        val top = startY
        val bottom = top + bodySize + 14f
        canvas.drawRect(RectF(margin, top, pageWidth - margin, bottom), bg)

        canvas.drawText("Event Date & Time", margin + 8f, bottom - 6f, text)
        canvas.drawText("Trigger Time", margin + 200f, bottom - 6f, text)
        val ox = pageWidth - margin - text.measureText("Offset")
        canvas.drawText("Offset", ox, bottom - 6f, text)

        return bottom + 10f
    }

    private fun drawColumnHeaderFlat(canvas: Canvas, startY: Float): Float {
        // Columns: Event (icon+title) | Event Date | Trigger Time | Offset
        val bg = Paint().apply { color = Color.rgb(245, 245, 245) }
        val text = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = bodySize
        }

        val top = startY
        val bottom = top + bodySize + 14f
        canvas.drawRect(RectF(margin, top, pageWidth - margin, bottom), bg)

        canvas.drawText("Event", margin + 8f, bottom - 6f, text)
        canvas.drawText("Event Date & Time", margin + 220f, bottom - 6f, text)
        canvas.drawText("Trigger Time", margin + 360f, bottom - 6f, text)
        val ox = pageWidth - margin - text.measureText("Offset")
        canvas.drawText("Offset", ox, bottom - 6f, text)

        return bottom + 10f
    }

    // =============================================================
    // Header & Footer
    // =============================================================
    private fun drawHeader(canvas: Canvas, titleText: String, generatedAtStr: String) {
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = titleSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(titleText, margin, margin + titleSize, titlePaint)

        val datePaint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = smallSize
        }
        val dateText = "Generated: $generatedAtStr"
        val dx = pageWidth - margin - datePaint.measureText(dateText)
        canvas.drawText(dateText, dx, margin + smallSize, datePaint)

        val linePaint = Paint().apply {
            color = headerLineColor
            strokeWidth = 1f
        }
        val y = margin + titleSize + headerExtra / 2f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int, totalPages: Int, generated: String) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = smallSize
        }
        val y = pageHeight - margin - footerExtra / 2f
        canvas.drawText("Event Reminder System", margin, y, paint)
        val center = "Generated: $generated"
        val cx = (pageWidth / 2f) - (paint.measureText(center) / 2f)
        canvas.drawText(center, cx, y, paint)
        val page = "Page $pageNumber/$totalPages"
        val px = pageWidth - margin - paint.measureText(page)
        canvas.drawText(page, px, y, paint)
    }

    // =============================================================
    // Summary box (final page)
    // =============================================================
    private fun drawSummaryBox(canvas: Canvas, top: Float, report: ActiveAlarmReport) {
        val left = margin
        val right = pageWidth - margin
        val bottom = top + 120f
        val boxPaint = Paint().apply { color = Color.argb(18, 0, 0, 0) }
        canvas.drawRect(RectF(left, top, right, bottom), boxPaint)

        val headerPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = groupHeaderSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = bodySize
        }

        val total = report.groupedByTitle.sumOf { it.alarms.size }
        val earliest = report.sortedAlarms.minByOrNull { it.nextTrigger }?.nextTrigger
        val latest = report.sortedAlarms.maxByOrNull { it.nextTrigger }?.nextTrigger
        val eStr = earliest?.let { formatDate(it) } ?: "-"
        val lStr = latest?.let { formatDate(it) } ?: "-"

        var y = top + 28f
        canvas.drawText("Summary", left + 12f, y, headerPaint)
        y += groupHeaderSize + 4f
        canvas.drawText("Total alarms: $total", left + 12f, y, bodyPaint)
        y += bodySize + 6f
        canvas.drawText("Earliest: $eStr", left + 12f, y, bodyPaint)
        y += bodySize + 6f
        canvas.drawText("Latest: $lStr", left + 12f, y, bodyPaint)
        y += bodySize + 6f
        canvas.drawText("Sections: ${report.groupedByTitle.size}", left + 12f, y, bodyPaint)
    }

    // =============================================================
    // Helpers
    // =============================================================
    private fun formatDate(epochMillis: Long): String {
        val zdt = java.time.Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        return alarmDateFormatter.format(zdt)
    }

    private fun formatOffset(minutes: Long): String {
        if (minutes == 0L) return "At time"
        val days = minutes / 1440
        val hours = (minutes % 1440) / 60
        val mins = minutes % 60
        val parts = mutableListOf<String>()
        if (days > 0) parts.add("$days day${if (days > 1) "s" else ""}")
        if (hours > 0) parts.add("$hours hour${if (hours > 1) "s" else ""}")
        if (mins > 0) parts.add("$mins minute${if (mins > 1) "s" else ""}")
        return parts.joinToString(" ") + " before"
    }

    private fun computeBaselineForRow(paint: Paint, rowTop: Float, bandHeight: Float): Float {
        val fm = paint.fontMetrics
        val center = rowTop + bandHeight / 2f
        return center - (fm.ascent + fm.descent) / 2f
    }

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

    private fun Context.getStringResourceOrDefault(name: String, default: String): String =
        try {
            val id = resources.getIdentifier(name, "string", packageName)
            if (id != 0) resources.getString(id) else default
        } catch (_: Throwable) {
            default
        }
}
