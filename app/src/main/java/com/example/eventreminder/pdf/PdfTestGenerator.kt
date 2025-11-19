package com.example.eventreminder.pdf

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class PdfTestGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun createBlankPdfTest(): Result<String> {
        return try {

            // ⭐ SAFE, guaranteed writable on Android 10–14
            val docsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)

            val targetFolder = File(docsDir, "ReminderReports")
            if (!targetFolder.exists()) targetFolder.mkdirs()

            val file = File(targetFolder, "pdf_test_${System.currentTimeMillis()}.pdf")

            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdf.startPage(pageInfo)
            pdf.finishPage(page)

            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()

            val realPath = file.absolutePath
            Log.d("PDF_TEST", "PDF saved at: $realPath")

            Result.success(realPath)

        } catch (e: Exception) {
            Timber.tag("PDF_TEST").e(e, "Error creating blank PDF")
            Result.failure(e)
        }
    }
}
