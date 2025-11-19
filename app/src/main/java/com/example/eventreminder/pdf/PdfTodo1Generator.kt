package com.example.eventreminder.pdf

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class PdfTodo1Generator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun generateTestReport(report: ActiveAlarmReport): Result<String> {
        return try {

            val docs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val folder = File(docs, "ReminderReports")
            folder.mkdirs()

            val file = File(folder, "todo1_report_${System.currentTimeMillis()}.pdf")

            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint().apply {
                textSize = 14f
                isAntiAlias = true
            }

            var y = 40

            canvas.drawText("PDF TODO-1 TEST REPORT", 20f, y.toFloat(), paint)
            y += 40

            canvas.drawText("Generated: ${report.generatedAt}", 20f, y.toFloat(), paint)
            y += 40

            canvas.drawText("Sorted Alarms:", 20f, y.toFloat(), paint)
            y += 30

            report.sortedAlarms.forEach {
                canvas.drawText(
                    "- ${it.eventTitle} at ${it.nextTrigger} (${it.offsetMinutes} min)",
                    25f,
                    y.toFloat(),
                    paint
                )
                y += 25
            }

            pdf.finishPage(page)

            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()

            Result.success(file.absolutePath)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
