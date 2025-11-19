package com.example.eventreminder.pdf

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * =============================================================
 * PdfTodo2Generator
 *
 * Produces a 2-page modern report:
 *  - Page 1: Grouped by Title (A→Z), alarms inside each title sorted soonest-first
 *  - Page 2: All alarms sorted soonest-first
 *
 * Design choices:
 *  - Title 24sp, Section headers 18sp bold, Body 13sp
 *  - Header: "Event Reminder Report — <date>"
 *  - Footer: Generated timestamp + Page X/Y
 *  - Thin grey separators between sections
 * =============================================================
 */
class PdfTodo2Generator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val pageWidth = 595   // A4-like width in points
    private val pageHeight = 842  // A4-like height in points
    private val margin = 40f

    // Typography sizes (points)
    private val titleSize = 24f
    private val sectionSize = 18f
    private val bodySize = 13f
    private val smallSize = 11f

    private val headerLineColor = Color.argb(30, 0, 0, 0) // faint line
    private val separatorColor = Color.argb(60, 120, 120, 120) // thin grey line

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /**
     * =============================================================
     * Function: generateReportPdf()
     * - report: ActiveAlarmReport (grouped + sorted)
     * Returns Result.success(path) or Result.failure(exception)
     * =============================================================
     */
    fun generateReportPdf(report: ActiveAlarmReport): Result<String> {
        return try {
            val docs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val folder = File(docs, "ReminderReports")
            if (!folder.exists()) folder.mkdirs()

            val file = File(folder, "report_todo2_${System.currentTimeMillis()}.pdf")

            val pdf = PdfDocument()

            // Page 1: Grouped by Title
            val pageInfo1 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page1 = pdf.startPage(pageInfo1)
            drawGroupedPage(page1.canvas, report, 1, 2)
            pdf.finishPage(page1)

            // Page 2: All alarms soonest-first
            val pageInfo2 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
            val page2 = pdf.startPage(pageInfo2)
            drawFlatPage(page2.canvas, report, 2, 2)
            pdf.finishPage(page2)

            // Write PDF
            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()

            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------
    // Helpers: draw page header & footer
    // -------------------------
    private fun drawHeader(canvas: Canvas, titleText: String) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = titleSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // Title (left)
        canvas.drawText(titleText, margin, margin + titleSize, paint)

        // Date (right)
        val datePaint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = smallSize.toFloat()
        }
        val generatedAt = dateFormatter.format(reportNow())
        val rightText = "Generated: $generatedAt"
        val textWidth = datePaint.measureText(rightText)
        canvas.drawText(rightText, pageWidth - margin - textWidth, margin + smallSize, datePaint)

        // thin line
        val linePaint = Paint().apply {
            color = headerLineColor
            strokeWidth = 1f
        }
        val y = margin + titleSize + 8f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int, totalPages: Int, generatedAtStr: String) {
        val footerPaint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = smallSize
        }

        val footerY = pageHeight - margin + 10f

        // Left footer text
        val leftText = "Event Reminder System"
        canvas.drawText(leftText, margin, footerY, footerPaint)

        // Center timestamp
        val midText = "Generated: $generatedAtStr"
        val midX = (pageWidth / 2f) - (footerPaint.measureText(midText) / 2f)
        canvas.drawText(midText, midX, footerY, footerPaint)

        // Page number (right)
        val rightText = "Page $pageNumber/$totalPages"
        val rightX = pageWidth - margin - footerPaint.measureText(rightText)
        canvas.drawText(rightText, rightX, footerY, footerPaint)
    }

    private fun reportNow() = java.time.LocalDateTime.now()

    // -------------------------
    // Page 1: Grouped by Title
    // -------------------------
    private fun drawGroupedPage(canvas: Canvas, report: ActiveAlarmReport, pageNumber: Int, totalPages: Int) {
        val generatedAtStr = dateFormatter.format(report.generatedAt)
        // header
        val headerTitle = "Event Reminder Report — ${context.getStringResourceOrDefault("app_name", "Event Reminder")}"
        drawHeader(canvas, headerTitle)

        // start content under header
        var y = margin + titleSize + 30f

        // paint definitions
        val sectionPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = sectionSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = bodySize
        }
        val smallPaint = Paint().apply {
            isAntiAlias = true
            color = Color.GRAY
            textSize = smallSize.toFloat()
        }
        val separatorPaint = Paint().apply {
            color = separatorColor
            strokeWidth = 1f
        }

        // iterate groups
        val groups = report.groupedByTitle
        for ((index, group) in groups.withIndex()) {
            // Section title
            canvas.drawText(group.title, margin, y, sectionPaint)
            y += sectionSize + 8f

            // alarms inside
            val sortedAlarms = group.alarms.sortedBy { it.nextTrigger }
            for (alarm in sortedAlarms) {
                // line: time — title — offset
                val timeStr = alarm.nextTrigger.toString()
                val left = "${timeStr}  "
                val mid = alarm.eventTitle
                val right = "${alarm.offsetMinutes} min"

                // draw left (time)
                canvas.drawText(left, margin + 8f, y, bodyPaint)

                // draw mid (title) with wrap if needed
                val midX = margin + 140f
                canvas.drawText(mid, midX, y, bodyPaint)

                // draw right aligned offset
                val rightPaint = bodyPaint
                val rightTextWidth = rightPaint.measureText(right)
                canvas.drawText(right, pageWidth - margin - rightTextWidth, y, rightPaint)

                y += bodySize + 6f

                // page break if needed
                if (y > pageHeight - margin - 80f) {
                    // draw footer for this page and start a new one (not implemented multi-page inside grouped)
                    // For TODO-2 we expect content to fit; otherwise real pagination logic is required.
                    // We will stop adding more to keep two pages exact.
                    break
                }
            }

            // separator line between sections
            canvas.drawLine(margin, y + 4f, pageWidth - margin, y + 4f, separatorPaint)
            y += 18f

            // small safety break if out of space
            if (y > pageHeight - margin - 80f) {
                break
            }
        }

        // footer
        drawFooter(canvas, pageNumber, totalPages, generatedAtStr)
    }

    // -------------------------
    // Page 2: Flat sorted list
    // -------------------------
    private fun drawFlatPage(canvas: Canvas, report: ActiveAlarmReport, pageNumber: Int, totalPages: Int) {
        val generatedAtStr = dateFormatter.format(report.generatedAt)
        val headerTitle = "Event Reminder Report — ${context.getStringResourceOrDefault("app_name", "Event Reminder")}"
        drawHeader(canvas, headerTitle)

        var y = margin + titleSize + 30f

        val bodyPaint = Paint().apply {
            isAntiAlias = true
            color = Color.DKGRAY
            textSize = bodySize
        }
        val sectionPaint = Paint().apply {
            isAntiAlias = true
            color = Color.BLACK
            textSize = sectionSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val separatorPaint = Paint().apply {
            color = separatorColor
            strokeWidth = 1f
        }

        canvas.drawText("All Alarms (Soonest First)", margin, y, sectionPaint)
        y += sectionSize + 12f

        val alarms = report.sortedAlarms
        for (alarm in alarms) {
            val timeStr = alarm.nextTrigger.toString()
            val line = "${timeStr} - ${alarm.eventTitle} (offset ${alarm.offsetMinutes}m)"
            canvas.drawText(line, margin + 8f, y, bodyPaint)
            y += bodySize + 8f

            // separator
            canvas.drawLine(margin + 8f, y + 2f, pageWidth - margin - 8f, y + 2f, separatorPaint)
            y += 10f

            if (y > pageHeight - margin - 80f) {
                // stop — no additional pages in TODO-2
                break
            }
        }

        // Summary box at bottom (compact)
        val summaryStartY = pageHeight - margin - 120f
        if (summaryStartY > y + 20f) {
            val boxPaint = Paint().apply {
                color = Color.argb(20, 0, 0, 0)
            }
            val rectLeft = margin
            val rectTop = summaryStartY
            val rectRight = pageWidth - margin
            val rectBottom = pageHeight - margin - 30f
            canvas.drawRect(RectF(rectLeft, rectTop, rectRight, rectBottom), boxPaint)

            val summaryPaint = Paint().apply {
                color = Color.BLACK
                textSize = bodySize
                isAntiAlias = true
            }

            val totalEvents = report.groupedByTitle.sumOf { it.alarms.size }
            val earliest = report.sortedAlarms.minByOrNull { it.nextTrigger }?.nextTrigger?.toString() ?: "-"
            val latest = report.sortedAlarms.maxByOrNull { it.nextTrigger }?.nextTrigger?.toString() ?: "-"

            var sy = rectTop + 24f
            canvas.drawText("Summary", rectLeft + 12f, sy, sectionPaint)
            sy += sectionSize
            canvas.drawText("Total events: $totalEvents", rectLeft + 12f, sy, summaryPaint)
            sy += bodySize + 6f
            canvas.drawText("Earliest: $earliest", rectLeft + 12f, sy, summaryPaint)
            sy += bodySize + 6f
            canvas.drawText("Latest: $latest", rectLeft + 12f, sy, summaryPaint)
            sy += bodySize + 6f
            canvas.drawText("Sections: ${report.groupedByTitle.size}", rectLeft + 12f, sy, summaryPaint)
        }

        // footer
        drawFooter(canvas, pageNumber, totalPages, generatedAtStr)
    }

    // small helper to fetch a string resource if exists, else default
    private fun Context.getStringResourceOrDefault(name: String, default: String): String {
        return try {
            val id = resources.getIdentifier(name, "string", packageName)
            if (id != 0) resources.getString(id) else default
        } catch (t: Throwable) {
            default
        }
    }
}
