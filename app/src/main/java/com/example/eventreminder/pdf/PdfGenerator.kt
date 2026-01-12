package com.example.eventreminder.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.max

/**
 * =============================================================
 * PdfGenerator — FINAL with Description column
 *
 * - Page-1 columns: Event Date & Time | Description / Name | Trigger Time | Offset
 * - Page-2 columns: Event (icon + title) | Description / Name | Event Date & Time | Trigger Time | Offset
 * - Description: uses EventReminder.description; displays "-" if null
 * - Group icon = emoji of the group's FIRST event
 * - Pagination automatic; summary only on final page
 * =============================================================
 */
class PdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // Page geometry (A4-like)
    private val pageWidth = 500 //595 // A4 size
    private val pageHeight = 842

    // Outer margins & breathing
    private val margin = 44f                 // increased outer margin
    private val headerExtra = 20f            // extra breathing below header
    private val footerExtra = 22f            // extra breathing above footer

    // Typography sizes (points)
    // Reduced title size as requested (smaller than original)
    private val titleSize = 12f              // report title (reduced)
    private val groupHeaderSize = 13f        // group header (bold, slightly larger than row)
    private val bodySize = 12f               // row font
    private val smallSize = 11f              // footer / small text

    // Row band heights — medium spacing (1.4x) plus breathing for description column
    private val rowBandHeight = bodySize * 1.4f + 14f
    private val groupHeaderBand = groupHeaderSize + 14f

    private val headerLineColor = Color.argb(28, 0, 0, 0)
    private val separatorColor = Color.argb(70, 200, 200, 200)

    private val headerDateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    private val alarmDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

    // Emoji mappings (fallbacks; group icon will use first event emoji if group has alarms)
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
        //"Work" to "💼",
        //"Other" to "📌"
    )

    // =============================================================
    // Public - generate report (saves in PUBLIC Documents folder)
    // =============================================================
    /**
     * Caller:
     *  - PdfViewModel.runTodo3RealReport()
     *
     * Responsibility:
     *  - Generates the main Alarm PDF report.
     *  - Renders grouped pages followed by flat pages.
     *  - Calculates total page count before rendering.
     *  - Persists the PDF into public Documents via MediaStore.
     *
     * Input:
     *  - context: Application context used for ContentResolver access.
     *  - report: ActiveAlarmReport containing grouped + flat alarm data.
     *
     * Output:
     *  - Result<Uri> pointing to the generated PDF file.
     *
     * Side Effects:
     *  - Writes a PDF file to device storage.
     */
    fun generateAlarmsReportPdf(context: Context, report: ActiveAlarmReport): Result<Uri> {
        return try {
            val resolver = context.contentResolver
            val relativePath = Environment.DIRECTORY_DOCUMENTS
            val fileName = "Report_AllAlarms_${System.currentTimeMillis()}.pdf"
            val collection = MediaStore.Files.getContentUri("external")

            val targetUri = resolver.insert(collection, ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }) ?: return Result.failure(IllegalStateException("Failed to create MediaStore entry"))

            val pdf = PdfDocument()
            val totalPages = simulatePageCounts(report)

            var pageNumber = 1
            pageNumber = renderGroupedPages(pdf, report, pageNumber, totalPages)

            resolver.openOutputStream(targetUri, "w")?.use { outputStream ->
                pdf.writeTo(outputStream)
            }
            pdf.close()

            Result.success(targetUri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Caller:
     *  - generateReportPdf()
     *
     * Responsibility:
     *  - Simulates how many pages are required before rendering.
     *  - Accounts for header, footer, and row heights.
     *
     * Input:
     *  - report: ActiveAlarmReport containing alarm data.
     *
     * Output:
     *  - Pair<Int, Int> → (groupedPages, flatPages)
     *
     * Side Effects:
     *  - None (pure calculation).
     */
    private fun simulatePageCounts(report: ActiveAlarmReport): Int {
        // ---------------------------------------------------------
        // Compute usable vertical space (header + footer safe)
        // ---------------------------------------------------------
        val contentTop =
            margin + titleSize + headerExtra + (bodySize + 14f) // includes column header height

        val contentBottom =
            pageHeight - margin - footerExtra - smallSize

        val usableHeight = contentBottom - contentTop

        // ---------------------------------------------------------
        // Rows per page
        // ---------------------------------------------------------
        val rowsPerPage =
            max(1, (usableHeight / rowBandHeight).toInt())

        // ---------------------------------------------------------
        // Count grouped rows
        //  - 1 row per group header
        //  - 1 row per valid alarm
        // ---------------------------------------------------------
        var groupedRowCount = 0

        for (group in report.groupedByTitle) {
            if (group.alarms.isEmpty()) continue

            groupedRowCount += 1 // group header

            groupedRowCount += group.alarms.count {
                it.eventTitle.isNotBlank() && it.nextTrigger > 0L
            }
        }

        // ---------------------------------------------------------
        // Compute required pages
        // ---------------------------------------------------------
        return max(1, (groupedRowCount + rowsPerPage - 1) / rowsPerPage)
    }

    /**
     * Caller:
     *  - generateReportPdf()
     *
     * Responsibility:
     *  - Renders grouped alarm pages (Page 1 + overflow).
     *  - Handles group headers, rows, pagination, and summary.
     *  - Ensures footer-safe rendering.
     *
     * Input:
     *  - pdf: PdfDocument instance.
     *  - report: ActiveAlarmReport.
     *  - startPageNumber: Page number to begin rendering from.
     *  - totalPages: Total pages across grouped + flat.
     *
     * Output:
     *  - Next page number after finishing grouped pages.
     *
     * Side Effects:
     *  - Adds pages to PdfDocument.
     */
    private fun renderGroupedPages(
        pdf: PdfDocument,
        report: ActiveAlarmReport,
        startPageNumber: Int,
        totalPages: Int
    ): Int {

        if (report.groupedByTitle.isEmpty()) return startPageNumber

        var pageNumber = startPageNumber

        var page = pdf.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        var canvas = page.canvas

        val generatedAt = headerDateFormatter.format(report.generatedAt)
        val headerTitle =
            "Event Reminder Report — ${context.getStringResourceOrDefault("app_name", "Event Reminder")}"

        // column layout (Event Date & Time REMOVED)
        val columnWidths = listOf(
            200f, // Description
            150f, // Trigger Time
            100f  // Offset
        )

        val firstColumnX = margin + 6f

        // ✅ All columns LEFT aligned
        val columnStarts = buildList {
            var x = firstColumnX
            for (width in columnWidths) {
                add(x)
                x += width
            }
        }

        // Render header
        drawHeader(canvas, headerTitle, generatedAt)

        // First content line
        var y = margin + titleSize + headerExtra
        y = drawColumnHeaderGrouped(canvas, y)

        val groupPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = groupHeaderSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val body = TextPaint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = bodySize
        }
        val sepPaint = Paint().apply {
            color = separatorColor
            strokeWidth = 1f
        }

        val contentBottom = pageHeight - margin - footerExtra - smallSize

        // Loop through each group
        for (group in report.groupedByTitle) {

            if (group.alarms.isEmpty()) continue

            // Determine icon for group (first event)
            val firstAlarm = group.alarms.first()
            val groupEmoji = pickEventEmoji(firstAlarm.eventTitle)

            // ✅ Count ONLY rendered alarms (same filter as rows)
            val groupCount = group.alarms.count {
                it.eventTitle.isNotBlank() && it.nextTrigger > 0L
            }
            val groupText = "$groupEmoji  ${group.title} ($groupCount)"

            // Check if next header fits, else page-break
            if (y + groupHeaderBand > contentBottom) {
                drawFooter(canvas, pageNumber, totalPages, generatedAt)
                pdf.finishPage(page)

                pageNumber++
                page = pdf.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = page.canvas
                drawHeader(canvas, headerTitle, generatedAt)
                y = margin + titleSize + headerExtra
                y = drawColumnHeaderGrouped(canvas, y)
            }

            // Draw group header centered in its band
            val headerBaseline = computeBaselineForRow(groupPaint, y, groupHeaderBand)
            canvas.drawText(groupText, margin, headerBaseline, groupPaint)
            y += groupHeaderBand

            // Render sorted events of group
            val sorted = group.alarms.sortedBy { it.nextTrigger }

            for (alarm in sorted) {
                if (alarm.eventTitle.isBlank()) continue
                if (alarm.nextTrigger <= 0L) continue

                // Page-break before row
                if (y + rowBandHeight > contentBottom) {
                    drawFooter(canvas, pageNumber, totalPages, generatedAt)
                    pdf.finishPage(page)

                    pageNumber++
                    page = pdf.startPage(
                        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    )
                    canvas = page.canvas
                    drawHeader(canvas, headerTitle, generatedAt)
                    y = margin + titleSize + headerExtra
                    y = drawColumnHeaderGrouped(canvas, y)
                }

                val baseline = computeBaselineForRow(body, y, rowBandHeight)

                fun truncate(text: String, paint: TextPaint, maxWidth: Float): String {
                    return TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
                }

                // -------------------------------------------
                // Column 1 — Description / Name
                // -------------------------------------------
                val desc = alarm.description?.takeIf { it.isNotBlank() } ?: "-"
                canvas.drawText(truncate(desc, body, columnWidths[0]), columnStarts[0], baseline, body)

                // -------------------------------------------
                // Column 2 — Trigger Time
                // -------------------------------------------
                val trigText = formatDate(alarm.nextTrigger)
                canvas.drawText(truncate(trigText, body, columnWidths[1]), columnStarts[1], baseline, body)

                // -------------------------------------------
                // Column 3 — Offset (LEFT aligned)
                // -------------------------------------------
                val offsetText = formatOffset(alarm.offsetMinutes)
                canvas.drawText(truncate(offsetText, body, columnWidths[2]), columnStarts[2], baseline, body)

                y += rowBandHeight

                // horizontal separator
                if (group != report.groupedByTitle.last()) {
                    canvas.drawLine(margin, y + 1f, pageWidth - margin, y + 1f, sepPaint)
                }
                y += 8f
            }

            y += 6f
        }

        // FINAL PAGE: Summary block (grouped report always ends document)

        val summarySpacing = 16f
        val summaryHeight = getSummaryBoxHeight(report)

        if (y + summarySpacing + summaryHeight > contentBottom) {
            drawFooter(canvas, pageNumber, totalPages, generatedAt)
            pdf.finishPage(page)

            pageNumber++
            page = pdf.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            canvas = page.canvas
            drawHeader(canvas, headerTitle, generatedAt)
            y = margin + titleSize + headerExtra
        }

        drawSummaryBox(canvas, y + summarySpacing, report)

        // Finish last grouped page
        drawFooter(canvas, pageNumber, totalPages, generatedAt)
        pdf.finishPage(page)

        return pageNumber + 1
    }


    // =============================================================
    // COLUMN HEADER — GROUPED PAGE
    // =============================================================
    private fun drawColumnHeaderGrouped(canvas: Canvas, startY: Float): Float {
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

        // ✅ Updated columns (Event Date & Time REMOVED, all LEFT aligned)
        val columns = listOf(
            "Description"  to 200f,
            "Trigger Time" to 150f,
            "Offset"       to 100f
        )

        var x = margin + 6f
        for ((header, width) in columns) {
            canvas.drawText(header, x, bottom - 6f, text)
            x += width
        }

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

        // Footer text
        val footerTextPadding = 8f
        val y = pageHeight - margin + footerTextPadding

        canvas.drawText("Event Reminder System", margin, y, paint)

        val center = "Generated: $generated"
        val cx = (pageWidth / 2f) - (paint.measureText(center) / 2f)
        canvas.drawText(center, cx, y, paint)

        val page = "Page $pageNumber/$totalPages"
        val px = pageWidth - margin - paint.measureText(page)
        canvas.drawText(page, px, y, paint)
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
            //listOf("work").any { t.contains(it) } -> eventEmojiMap["work"]!!
            //listOf("other").any { t.contains(it) } -> eventEmojiMap["other"]!!
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

    private fun drawSummaryBox(canvas: Canvas, top: Float, report: ActiveAlarmReport) {

        val left = margin
        val right = pageWidth - margin

        // ---------------------------------------------------------
        // Calculate total events (same filter as rows)
        // ---------------------------------------------------------
        val totalEvents = report.groupedByTitle.sumOf { group ->
            group.alarms.count {
                it.eventTitle.isNotBlank() && it.nextTrigger > 0L
            }
        }

        // ---------------------------------------------------------
        // Compact fixed height for single-line summary
        // ---------------------------------------------------------
        val boxHeight = bodySize + 28f
        val bottom = top + boxHeight

        // ---------------------------------------------------------
        // Background
        // ---------------------------------------------------------
        val boxPaint = Paint().apply {
            color = Color.argb(22, 0, 0, 0)
        }
        canvas.drawRect(RectF(left, top, right, bottom), boxPaint)

        // ---------------------------------------------------------
        // Text paint
        // ---------------------------------------------------------
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = bodySize
        }

        // ---------------------------------------------------------
        // Vertically centered baseline
        // ---------------------------------------------------------
        val fm = textPaint.fontMetrics
        val centerY = top + boxHeight / 2f
        val baseline = centerY - (fm.ascent + fm.descent) / 2f

        // ---------------------------------------------------------
        // Draw ONLY total events
        // ---------------------------------------------------------
        canvas.drawText(
            "Total Events: $totalEvents",
            left + 16f,
            baseline,
            textPaint
        )
    }

    private fun getSummaryBoxHeight(report: ActiveAlarmReport): Float {
        // Only ONE line: "Total Events: X"
        return 30f +          // top padding
                bodySize +     // text height
                20f            // bottom padding
    }

}
