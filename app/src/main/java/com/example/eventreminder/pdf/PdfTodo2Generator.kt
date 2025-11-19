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

/**
 * =============================================================
 * PdfTodo2Generator (FINAL — SUMMARY ONLY ON PAGE 2)
 *
 * Features:
 *  ✔ Date format: 09 Apr 1970, 11:09 AM
 *  ✔ Offset: “1 day / 2 hours / 10 minutes before”
 *  ✔ Column headers on both pages
 *  ✔ Summary block at the END of PAGE 2 only
 *  ✔ Modern layout (grey header row)
 * =============================================================
 */
class PdfTodo2Generator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f

    private val titleSize = 24f
    private val sectionSize = 18f
    private val bodySize = 13f
    private val smallSize = 11f

    private val headerLineColor = Color.argb(30, 0, 0, 0)
    private val separatorColor = Color.argb(60, 120, 120, 120)

    private val headerDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val alarmDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")


    // =============================================================
    // PUBLIC ENTRY
    // =============================================================
    fun generateReportPdf(report: ActiveAlarmReport): Result<String> {
        return try {
            val docs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val folder = File(docs, "ReminderReports")
            if (!folder.exists()) folder.mkdirs()

            val file = File(folder, "report_todo2_${System.currentTimeMillis()}.pdf")
            val pdf = PdfDocument()

            // PAGE 1
            val p1 = pdf.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            )
            drawGroupedPage(p1.canvas, report, 1, 2)
            pdf.finishPage(p1)

            // PAGE 2
            val p2 = pdf.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
            )
            drawFlatPage(p2.canvas, report, 2, 2)
            pdf.finishPage(p2)

            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()

            Result.success(file.absolutePath)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun reportNow() = java.time.LocalDateTime.now()

    // =============================================================
    // HEADER
    // =============================================================
    private fun drawHeader(canvas: Canvas, titleText: String) {

        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = titleSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        canvas.drawText(titleText, margin, margin + titleSize, titlePaint)

        // Right side date
        val datePaint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = smallSize
        }

        val generated = headerDateFormatter.format(reportNow())
        val dateText = "Generated: $generated"
        val dx = pageWidth - margin - datePaint.measureText(dateText)
        canvas.drawText(dateText, dx, margin + smallSize, datePaint)

        // Line under header
        val linePaint = Paint().apply {
            color = headerLineColor
            strokeWidth = 1f
        }
        val y = margin + titleSize + 8f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
    }

    // =============================================================
    // FOOTER
    // =============================================================
    private fun drawFooter(canvas: Canvas, pageNumber: Int, totalPages: Int, generated: String) {

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = smallSize
        }

        val y = pageHeight - margin + 10f

        canvas.drawText("Event Reminder System", margin, y, paint)

        val center = "Generated: $generated"
        val cx = (pageWidth / 2f) - (paint.measureText(center) / 2f)
        canvas.drawText(center, cx, y, paint)

        val page = "Page $pageNumber/$totalPages"
        val px = pageWidth - margin - paint.measureText(page)
        canvas.drawText(page, px, y, paint)
    }


    // =============================================================
    // COLUMN HEADER (grey bar)
    // =============================================================
    private fun drawColumnHeader(canvas: Canvas, startY: Float): Float {

        val bg = Paint().apply { color = Color.rgb(235, 235, 235) }

        val text = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = bodySize
        }

        val top = startY
        val bottom = top + bodySize + 14f

        canvas.drawRect(RectF(margin, top, pageWidth - margin, bottom), bg)

        canvas.drawText("Time", margin + 8f, bottom - 6f, text)
        canvas.drawText("Event Title", margin + 160f, bottom - 6f, text)

        val ox = pageWidth - margin - text.measureText("Offset")
        canvas.drawText("Offset", ox, bottom - 6f, text)

        return bottom + 10f
    }


    // =============================================================
    // FORMATTERS
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


    // =============================================================
    // PAGE 1 — GROUPED
    // =============================================================
    private fun drawGroupedPage(
        canvas: Canvas,
        report: ActiveAlarmReport,
        pageNumber: Int,
        totalPages: Int
    ) {
        val generated = headerDateFormatter.format(report.generatedAt)

        val headerTitle =
            "Event Reminder Report — ${context.getStringResourceOrDefault("app_name", "Event Reminder")}"

        drawHeader(canvas, headerTitle)

        var y = margin + titleSize + 30f
        y = drawColumnHeader(canvas, y)

        val sectionPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = sectionSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val body = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = bodySize
        }

        val sep = Paint().apply {
            color = separatorColor
            strokeWidth = 1f
        }

        for (group in report.groupedByTitle) {

            canvas.drawText(group.title, margin, y, sectionPaint)
            y += sectionSize + 8f

            val sorted = group.alarms.sortedBy { it.nextTrigger }

            for (alarm in sorted) {

                val timeText = formatDate(alarm.nextTrigger)
                val offsetText = formatOffset(alarm.offsetMinutes)

                canvas.drawText(timeText, margin + 8f, y, body)
                canvas.drawText(alarm.eventTitle, margin + 160f, y, body)

                val ox = pageWidth - margin - body.measureText(offsetText)
                canvas.drawText(offsetText, ox, y, body)

                y += bodySize + 8f

                // STOP page 1 early — leave no summary here
                if (y > pageHeight - margin - 60f) break
            }

            canvas.drawLine(margin, y + 2f, pageWidth - margin, y + 2f, sep)
            y += 18f
        }

        drawFooter(canvas, pageNumber, totalPages, generated)
    }


    // =============================================================
    // PAGE 2 — FLAT LIST + SUMMARY AT BOTTOM ONLY
    // =============================================================
    private fun drawFlatPage(
        canvas: Canvas,
        report: ActiveAlarmReport,
        pageNumber: Int,
        totalPages: Int
    ) {

        val generated = headerDateFormatter.format(report.generatedAt)

        val headerTitle =
            "Event Reminder Report — ${context.getStringResourceOrDefault("app_name", "Event Reminder")}"

        drawHeader(canvas, headerTitle)

        var y = margin + titleSize + 30f
        y = drawColumnHeader(canvas, y)

        val body = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = bodySize
        }

        val sep = Paint().apply {
            color = separatorColor
            strokeWidth = 1f
        }

        for (alarm in report.sortedAlarms) {

            val timeText = formatDate(alarm.nextTrigger)
            val offsetText = formatOffset(alarm.offsetMinutes)

            canvas.drawText(timeText, margin + 8f, y, body)
            canvas.drawText(alarm.eventTitle, margin + 160f, y, body)

            val ox = pageWidth - margin - body.measureText(offsetText)
            canvas.drawText(offsetText, ox, y, body)

            y += bodySize + 8f

            canvas.drawLine(margin, y + 2f, pageWidth - margin, y + 2f, sep)
            y += 12f

            // Keep space for summary block
            if (y > pageHeight - margin - 160f) break
        }

        // ===========================================
        // SUMMARY ONLY ON PAGE 2
        // ===========================================
        drawSummaryBox(canvas, pageHeight - margin - 140f, report)

        drawFooter(canvas, pageNumber, totalPages, generated)
    }


    // =============================================================
    // SUMMARY BOX (USED ONLY ON PAGE 2)
    // =============================================================
    private fun drawSummaryBox(canvas: Canvas, top: Float, report: ActiveAlarmReport) {

        val left = margin
        val right = pageWidth - margin
        val bottom = top + 120f

        val box = Paint().apply { color = Color.argb(20, 0, 0, 0) }

        canvas.drawRect(RectF(left, top, right, bottom), box)

        val headerPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = sectionSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val text = Paint().apply {
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

        y += sectionSize + 4f
        canvas.drawText("Total alarms: $total", left + 12f, y, text)
        y += bodySize + 6f

        canvas.drawText("Earliest: $eStr", left + 12f, y, text)
        y += bodySize + 6f

        canvas.drawText("Latest: $lStr", left + 12f, y, text)
        y += bodySize + 6f

        canvas.drawText("Sections: ${report.groupedByTitle.size}", left + 12f, y, text)
    }


    // =============================================================
    // RESOURCE HELPER
    // =============================================================
    private fun Context.getStringResourceOrDefault(name: String, default: String): String =
        try {
            val id = resources.getIdentifier(name, "string", packageName)
            if (id != 0) resources.getString(id) else default
        } catch (_: Throwable) {
            default
        }
}
