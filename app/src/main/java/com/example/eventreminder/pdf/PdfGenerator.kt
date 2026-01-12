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
    private val pageWidth = 700 //595 // A4 size
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
    private val flatPageBodySize = 10f               // row font
    private val smallSize = 11f              // footer / small text

    // Emoji icon size
    private val iconSize = 12f

    // Row band heights — medium spacing (1.4x) plus breathing for description column
    private val rowBandHeight = bodySize * 1.4f + 14f
    private val groupHeaderBand = groupHeaderSize + 14f

    private val headerLineColor = Color.argb(28, 0, 0, 0)
    private val separatorColor = Color.argb(70, 200, 200, 200)

    private val headerDateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
    private val alarmDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")

    private val summaryBoxHeight = 125f
    private val summaryBottomPadding = 24f

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
            val (groupedPagesCount, flatPagesCount) = simulatePageCounts(report)
            val totalPages = groupedPagesCount + flatPagesCount

            var pageNumber = 1
            pageNumber = renderGroupedPages(pdf, report, pageNumber, groupedPagesCount, totalPages, drawSummary = flatPagesCount == 0)
            //pageNumber = renderFlatPages(pdf, report, pageNumber, flatPagesCount, totalPages, drawSummary = true)

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
     *  - renderGroupedPages()
     *  - renderFlatPages()
     *  - renderReminderListPages()
     *
     * Responsibility:
     *  - Computes a footer-safe Y position for the summary box.
     *  - Ensures summary never overlaps footer content.
     *
     * Output:
     *  - Float Y-coordinate for summary box top.
     *
     * Side Effects:
     *  - None (pure calculation).
     */
    private fun computeSummaryTop(): Float {
        return pageHeight -
                margin -
                footerExtra -
                summaryBottomPadding -
                summaryBoxHeight
    }

    /**
     * Caller:
     *  - PdfViewModel.runReminderListReport()
     *
     * Responsibility:
     *  - Generates the Reminder List PDF (no offsets, no alarms).
     *  - Delegates rendering to renderReminderListPages().
     *  - Saves the PDF into public Documents via MediaStore.
     *
     * Input:
     *  - context: Application context for ContentResolver.
     *  - report: ReminderListReport containing grouped reminder rows.
     *
     * Output:
     *  - Result<Uri> pointing to the generated PDF file.
     *
     * Side Effects:
     *  - Writes a PDF file to device storage.
     */
    /*fun generateReminderListPdf(context: Context, report: ReminderListReport): Result<Uri> {
        return try {
            val resolver = context.contentResolver
            val fileName = "reminder_list_${System.currentTimeMillis()}.pdf"

            val uri = resolver.insert(
                MediaStore.Files.getContentUri("external"),
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                }
            ) ?: return Result.failure(
                IllegalStateException("MediaStore insert failed")
            )

            val pdf = PdfDocument()

            // 🔹 render reminder list pages (your existing logic)
            renderReminderListPages(
                pdf = pdf,
                report = report
            )

            resolver.openOutputStream(uri)?.use {
                pdf.writeTo(it)
            }
            pdf.close()

            Result.success(uri)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }*/

    /**
     * Caller:
     *  - generateReminderListPdf()
     *
     * Responsibility:
     *  - Renders reminder list pages with pagination.
     *  - Ensures rows never overlap footer.
     *  - Draws section headers, rows, summary, and footer.
     *
     * Input:
     *  - pdf: PdfDocument instance being written.
     *  - report: ReminderListReport containing grouped reminder data.
     *
     * Side Effects:
     *  - Mutates PdfDocument by adding pages.
     */
    /*private fun renderReminderListPages(
        pdf: PdfDocument,
        report: ReminderListReport
    ) {
        var pageNumber = 1

        var page = pdf.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        var canvas = page.canvas

        val generatedAt = headerDateFormatter.format(report.generatedAt)

        drawHeader(
            canvas = canvas,
            titleText = "Reminder List Report",
            generatedAtStr = generatedAt
        )

        // First content Y position
        var y = margin + titleSize + headerExtra

        val titlePaint = Paint().apply {
            isAntiAlias = true
            textSize = groupHeaderSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = bodySize
            color = Color.DKGRAY
        }

        // ---------------------------------------------------------
        // Footer-safe bottom boundary (CRITICAL)
        // Matches Alarm PDF spacing philosophy
        // ---------------------------------------------------------
        val contentBottom =
            pageHeight - margin - footerExtra - smallSize

        for (section in report.groupedByTitle) {

            // -----------------------------------------------------
            // Page break BEFORE section header if needed
            // -----------------------------------------------------
            if (y + groupHeaderBand > contentBottom) {
                drawFooter(canvas, pageNumber, pageNumber, generatedAt)
                pdf.finishPage(page)

                pageNumber++
                page = pdf.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = page.canvas

                drawHeader(canvas, "Reminder List Report", generatedAt)
                y = margin + titleSize + headerExtra
            }

            val headerBaseline =
                computeBaselineForRow(titlePaint, y, groupHeaderBand)

            canvas.drawText(section.title, margin, headerBaseline, titlePaint)
            y += groupHeaderBand

            for (row in section.reminders) {

                // -------------------------------------------------
                // Page break BEFORE row if needed
                // -------------------------------------------------
                if (y + rowBandHeight > contentBottom) {
                    drawFooter(canvas, pageNumber, pageNumber, generatedAt)
                    pdf.finishPage(page)

                    pageNumber++
                    page = pdf.startPage(
                        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    )
                    canvas = page.canvas

                    drawHeader(canvas, "Reminder List Report", generatedAt)
                    y = margin + titleSize + headerExtra
                }

                val baseline =
                    computeBaselineForRow(bodyPaint, y, rowBandHeight)

                canvas.drawText(row.shortId, margin, baseline, bodyPaint)

                canvas.drawText(
                    ellipsize(row.description, bodyPaint, 220f),
                    margin + 120f,
                    baseline,
                    bodyPaint
                )

                canvas.drawText(
                    row.eventDateTime,
                    margin + 360f,
                    baseline,
                    bodyPaint
                )

                y += rowBandHeight
            }

            // Visual breathing between sections
            y += 8f
        }

        // ---------------------------------------------------------
        // Summary (footer-safe)
        // ---------------------------------------------------------
        drawSimpleSummaryBox(
            canvas = canvas,
            top = computeSummaryTop(),
            totalReminders = report.groupedByTitle.sumOf { it.reminders.size },
            totalSections = report.groupedByTitle.size
        )

        drawFooter(canvas, pageNumber, pageNumber, generatedAt)
        pdf.finishPage(page)
    }*/

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
    private fun simulatePageCounts(report: ActiveAlarmReport): Pair<Int, Int> {
        // compute content top/bottom taking into account header/footer breathing and column header height
        val contentTop = margin + titleSize + headerExtra + (bodySize + 14f) // includes column header height
        val contentBottom = pageHeight - margin - footerExtra - smallSize
        val usableHeight = contentBottom - contentTop

        // compute how many data rows fit (rowsPerPage)
        val rowsPerPage = max(1, (usableHeight / rowBandHeight).toInt())

        // grouped rows: group header (1) + number of alarms (each alarm is one row)
        var groupedRowCount = 0
        for (group in report.groupedByTitle) {
            if (group.alarms.isEmpty()) continue
            groupedRowCount += 1 // group header row
            // only count alarms that have an actionable nextTrigger (>0)
            groupedRowCount += group.alarms.count { it.eventTitle.isNotBlank() && it.nextTrigger > 0L }
        }
        val groupedPages = max(1, (groupedRowCount + rowsPerPage - 1) / rowsPerPage)

        // flat rows: number of alarms that will be shown in flat list
        val flatRowCount = report.sortedAlarms.count { it.eventTitle.isNotBlank() && it.nextTrigger > 0L }
        val flatPages = max(1, (flatRowCount + rowsPerPage - 1) / rowsPerPage)

        return Pair(groupedPages, flatPages)
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
     *  - pagesCount: Total grouped pages.
     *  - totalPages: Total pages across grouped + flat.
     *  - drawSummary: Whether summary should be drawn.
     *
     * Output:
     *  - Next page number after finishing grouped pages.
     *
     * Side Effects:
     *  - Adds pages to PdfDocument.
     */
    private fun renderGroupedPages(pdf: PdfDocument, report: ActiveAlarmReport, startPageNumber: Int, pagesCount: Int, totalPages: Int, drawSummary: Boolean): Int {

        if (report.groupedByTitle.isEmpty()) return startPageNumber

        var pageNumber = startPageNumber

        var page = pdf.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        var canvas = page.canvas

        val generatedAt = headerDateFormatter.format(report.generatedAt)
        val headerTitle =
            "Event Reminder Report — ${context.getStringResourceOrDefault("app_name", "Event Reminder")}"

        // column layout (same as drawColumnHeaderGrouped)
        val columnWidths = listOf(
            200f, // Event Date & Time
            160f, // Description
            120f, // Trigger Time
            80f   // Offset
        )

        val firstColumnX = margin + 6f

        val columnStarts = buildList {
            var x = firstColumnX
            for (i in columnWidths.indices) {
                if (i == columnWidths.lastIndex) {
                    add(pageWidth - margin - columnWidths[i]) // right-align last column
                } else {
                    add(x)
                    x += columnWidths[i]
                }
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

            val groupText = "$groupEmoji  ${group.title}"

            // Check if next header fits, else page-break
            if (y + groupHeaderBand > contentBottom) {
                drawFooter(canvas, pageNumber, totalPages, generatedAt)
                pdf.finishPage(page)

                // new page
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

                    // new page
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
                // Column 1 — Event Date & Time
                // -------------------------------------------
                val eventDateEpoch = alarm.eventDateEpoch ?: alarm.nextTrigger
                val eventDateText = formatDate(eventDateEpoch)
                canvas.drawText(truncate(eventDateText, body, columnWidths[0]), columnStarts[0], baseline, body)

                // -------------------------------------------
                // Column 2 — Description / Name
                // -------------------------------------------
                val desc = alarm.description?.takeIf { it.isNotBlank() } ?: "-"
                canvas.drawText(truncate(desc, body, columnWidths[1]), columnStarts[1], baseline, body)

                // -------------------------------------------
                // Column 3 — Trigger Time
                // -------------------------------------------
                val trigText = formatDate(alarm.nextTrigger)
                canvas.drawText(truncate(trigText, body, columnWidths[2]), columnStarts[2], baseline, body)

                // -------------------------------------------
                // Column 4 — Offset (right aligned)
                // -------------------------------------------
                val offsetText = formatOffset(alarm.offsetMinutes)
                val offsetX = columnStarts[3] + columnWidths[3] - body.measureText(offsetText)
                canvas.drawText(offsetText, offsetX, baseline, body)

                y += rowBandHeight

                // horizontal separator
                canvas.drawLine(margin, y + 1f, pageWidth - margin, y + 1f, sepPaint)
                y += 8f
            }

            y += 6f
        }

        if (drawSummary) {
            val summaryTop = pageHeight - margin - footerExtra - 140f
            drawSummaryBox(canvas, summaryTop, report)
        }

        // Finish last grouped page
        drawFooter(canvas, pageNumber, totalPages, generatedAt)
        pdf.finishPage(page)

        return pageNumber + 1
    }

    /**
     * Caller:
     *  - generateReportPdf()
     *
     * Responsibility:
     *  - Renders flat alarm list pages with icons.
     *  - Handles pagination and footer safety.
     *  - Draws summary ONLY on the final page.
     *
     * Input:
     *  - pdf: PdfDocument instance.
     *  - report: ActiveAlarmReport.
     *  - startPageNumber: Page number to begin rendering.
     *  - pagesCount: Flat page count.
     *  - totalPages: Total pages across entire document.
     *  - drawSummary: Whether summary should be drawn.
     *
     * Output:
     *  - Next page number after finishing flat pages.
     *
     * Side Effects:
     *  - Adds pages to PdfDocument.
     */
    /*private fun renderFlatPages(pdf: PdfDocument, report: ActiveAlarmReport, startPageNumber: Int, pagesCount: Int, totalPages: Int, drawSummary: Boolean): Int {

        var pageNumber = startPageNumber

        var page = pdf.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        var canvas = page.canvas

        val generatedAtStr = headerDateFormatter.format(report.generatedAt)
        val headerTitle =
            "Event Reminder Report — ${context.getStringResourceOrDefault("app_name", "Event Reminder")}"

        // Define column widths (same as header layout)
        val columnWidths = listOf(
            180f, // Emoji + Title
            100f, // Description
            120f, // Event Date
            120f, // Trigger Time
            80f   // Offset
        )

        val firstColumnX = margin + 6f

        val columnStarts = buildList {
            var x = firstColumnX
            for (i in columnWidths.indices) {
                if (i == columnWidths.lastIndex) {
                    // Right-align last column
                    add(pageWidth - margin - columnWidths[i])
                } else {
                    add(x)
                    x += columnWidths[i]
                }
            }
        }


        drawHeader(canvas, headerTitle, generatedAtStr)

        var y = margin + titleSize + headerExtra
        y = drawColumnHeaderFlat(canvas, y)   // Draw "Event / Desc / EventDate / Trigger / Offset"

        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = flatPageBodySize
            color = Color.DKGRAY
        }
        val emojiPaint = Paint().apply {
            isAntiAlias = true
            textSize = iconSize
            color = Color.DKGRAY
        }
        val sepPaint = Paint().apply {
            color = separatorColor
            strokeWidth = 1f
        }

        val contentBottomLimit = pageHeight - margin - footerExtra - smallSize

        for (alarm in report.sortedAlarms) {

            if (alarm.eventTitle.isBlank()) continue
            if (alarm.nextTrigger <= 0L) continue

            // Page break check
            if (y + rowBandHeight > contentBottomLimit) {

                drawFooter(canvas, pageNumber, totalPages, generatedAtStr)
                pdf.finishPage(page)

                pageNumber++
                page = pdf.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = page.canvas

                drawHeader(canvas, headerTitle, generatedAtStr)

                y = margin + titleSize + headerExtra
                y = drawColumnHeaderFlat(canvas, y)
            }

            val baseline = computeBaselineForRow(bodyPaint, y, rowBandHeight)

            // Helper for truncation
            fun truncate(text: String, paint: TextPaint, maxWidth: Float): String {
                return TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()
            }

            // ---------------------------------------------------
            // Column 1 — Event (emoji + title)
            // ---------------------------------------------------
            val emoji = pickEventEmoji(alarm.eventTitle)
            val emojiBlockWidth =16f
            val titleMaxWidth = columnWidths[0] - emojiBlockWidth - 6f
            val titleX = columnStarts[0] + emojiBlockWidth + 6f
            canvas.drawText(emoji, columnStarts[0], baseline, emojiPaint)
            canvas.drawText(truncate(alarm.eventTitle, bodyPaint, titleMaxWidth), titleX, baseline, bodyPaint)

            // ---------------------------------------------------
            // Column 2 — Description (or "-")
            // ---------------------------------------------------
            val desc = alarm.description?.takeIf { it.isNotBlank() } ?: "-"
            canvas.drawText(truncate(desc, bodyPaint, columnWidths[1]), columnStarts[1], baseline, bodyPaint)

            // ---------------------------------------------------
            // Column 3 — Event Date & Time
            // ---------------------------------------------------
            val eventDateText = formatDate(alarm.eventDateEpoch ?: alarm.nextTrigger)
            canvas.drawText(truncate(eventDateText, bodyPaint, columnWidths[2]), columnStarts[2], baseline, bodyPaint)

            // ---------------------------------------------------
            // Column 4 — Trigger Time
            // ---------------------------------------------------
            val triggerText = formatDate(alarm.nextTrigger)
            canvas.drawText(truncate(triggerText, bodyPaint, columnWidths[3]), columnStarts[3], baseline, bodyPaint)

            // ---------------------------------------------------
            // Column 5 — Offset (right aligned)
            // ---------------------------------------------------
            val offsetText = formatOffset(alarm.offsetMinutes)
            val offsetX = columnStarts[4] + columnWidths[4] - bodyPaint.measureText(offsetText)
            canvas.drawText(offsetText, offsetX, baseline, bodyPaint)

            // Row separator
            y += rowBandHeight
            canvas.drawLine(margin, y + 1f, pageWidth - margin, y + 1f, sepPaint)
            y += 6f
        }

        // ---------------------------------------------------
        // FINAL PAGE: Summary block (only here)
        // ---------------------------------------------------
        if (drawSummary && pageNumber == totalPages) {
            drawSummaryBox(canvas, computeSummaryTop(), report)
        }

        drawFooter(canvas, pageNumber, totalPages, generatedAtStr)
        pdf.finishPage(page)

        return pageNumber + 1
    }*/

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

        // ✅ Define columns as header + width pairs
        val columns = listOf(
            "Event Date & Time" to 200f,
            "Description"       to 160f,
            "Trigger Time"      to 120f,
            "Offset"            to 80f
        )

        var x = margin + 6f
        for ((i, column) in columns.withIndex()) {
            val (header, width) = column
            val startX = if (i == columns.lastIndex) {
                // Right-align last column
                pageWidth - margin - text.measureText(header)
            } else {
                x
            }
            canvas.drawText(header, startX, bottom - 6f, text)
            if (i < columns.lastIndex) x += width
        }

        return bottom + 10f
    }

    // =============================================================
    // COLUMN HEADER — FLAT PAGE (Event | Desc | EventDate | Trigger | Offset)
    // =============================================================
    private fun drawColumnHeaderFlat(canvas: Canvas, startY: Float): Float {
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

        val columns: List<Pair<String, Float>> = listOf(
            "Event" to 180f,
            "Description" to 100f,
            "Event Date & Time" to 120f,
            "Trigger Time" to 120f,
            "Offset" to 80f
        )

        var x = margin + 6f
        for ((i, column) in columns.withIndex()) {
            val (header, width) = column
            val startX = if (i == columns.lastIndex) {
                // Right-align last column
                pageWidth - margin - text.measureText(header)
            } else {
                x
            }
            canvas.drawText(header, startX, bottom - 6f, text)
            if (i < columns.lastIndex) x += width
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

        // ---------------------------------------------------------
        // Separator line above footer (NEW)
        // ---------------------------------------------------------
        val linePaint = Paint().apply {
            isAntiAlias = true
            color = Color.argb(40, 0, 0, 0)   // subtle, light line
            strokeWidth = 1f
        }

        val lineY = pageHeight - margin - footerExtra
        canvas.drawLine(margin, lineY, pageWidth - margin, lineY, linePaint)

        // ---------------------------------------------------------
        // Footer text
        // ---------------------------------------------------------
        val footerTextPadding = 8f
        val y = lineY + footerTextPadding + smallSize

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

    /*private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String {
        return TextUtils.ellipsize(
            text,
            paint,
            maxWidth,
            TextUtils.TruncateAt.END
        ).toString()
    }*/

    /*private fun drawSimpleSummaryBox(canvas: Canvas, top: Float, totalReminders: Int, totalSections: Int) {
        val left = margin
        val right = pageWidth - margin
        val bottom = top + 80f

        val boxPaint = Paint().apply { color = Color.argb(20, 0, 0, 0) }
        canvas.drawRect(RectF(left, top, right, bottom), boxPaint)

        // Header text (bold)
        val headerPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = groupHeaderSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Body text (regular)
        val bodyPaint = Paint().apply {
            isAntiAlias = true
            textSize = bodySize
            color = Color.DKGRAY
        }

        // Print Summary
        var y = top + 28f
        canvas.drawText("Summary", left + 16f, y, headerPaint)

        y += groupHeaderSize + 6f
        canvas.drawText("Total Reminders: $totalReminders", left + 16f, y, bodyPaint)

        y += bodySize + 6f
        canvas.drawText("Sections: $totalSections", left + 16f, y, bodyPaint)
    }*/

    private fun drawSummaryBox(canvas: Canvas, top: Float, report: ActiveAlarmReport) {
        val left = margin
        val right = pageWidth - margin
        val bottom = top + 125f  // summary height

        // Light grey background
        val boxPaint = Paint().apply { color = Color.argb(22, 0, 0, 0) }
        canvas.drawRect(RectF(left, top, right, bottom), boxPaint)

        // Header text (bold)
        val headerPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = groupHeaderSize      // same as group title
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Body text (regular)
        val bodyPaint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = bodySize
        }

        // Extract summary data
        val totalEvents = report.groupedByTitle.sumOf { it.alarms.size }
        val earliest = report.sortedAlarms.minByOrNull { it.nextTrigger }?.nextTrigger
        val latest = report.sortedAlarms.maxByOrNull { it.nextTrigger }?.nextTrigger

        val earliestStr = earliest?.let { formatDate(it) } ?: "-"
        val latestStr = latest?.let { formatDate(it) } ?: "-"
        val totalSections = report.groupedByTitle.size

        // Print Summary
        var y = top + 30f
        canvas.drawText("Summary", left + 16f, y, headerPaint)

        y += groupHeaderSize + 4f
        canvas.drawText("Total Events: $totalEvents", left + 16f, y, bodyPaint)

        y += bodySize + 6f
        canvas.drawText("Earliest Event: $earliestStr", left + 16f, y, bodyPaint)

        y += bodySize + 6f
        canvas.drawText("Latest Event: $latestStr", left + 16f, y, bodyPaint)

        y += bodySize + 6f
        canvas.drawText("Sections: $totalSections", left + 16f, y, bodyPaint)
    }

}
