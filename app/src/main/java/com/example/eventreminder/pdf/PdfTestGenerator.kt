package com.example.eventreminder.pdf

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject


/**
 * =============================================================
 * TODO-0 : BLANK PDF RUN-TEST (FIRST STEP)
 *
 * Purpose:
 * - Create an empty PDF
 * - Save it in Documents/ReminderReports/
 * - Verify file creation & permissions
 * - This test does NOT draw any content yet
 *
 * Next Step will be TODO-1 (Data Models)
 * =============================================================
 */
class PdfTestGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * =========================================================
     * Function: createBlankPdfTest()
     * What it does:
     *  - Creates folder under /Documents/ReminderReports
     *  - Makes a one-page empty PDF
     *  - Saves and returns file URI
     * =========================================================
     */
    fun createBlankPdfTest(): Result<String> {
        return try {

            // STEP 1: Locate Documents folder
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

            // STEP 2: Create our app's subfolder
            val targetFolder = File(docsDir, "ReminderReports")
            if (!targetFolder.exists()) targetFolder.mkdirs()

            // STEP 3: Create a file inside the folder
            val file = File(targetFolder, "pdf_test_${System.currentTimeMillis()}.pdf")

            // STEP 4: Create a PDF document instance
            val pdfDocument = PdfDocument()

            // STEP 5: Start an empty page (page size = A4 default)
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)

            // NO DRAWING — this is intentionally blank
            pdfDocument.finishPage(page)

            // STEP 6: Write PDF to file
            FileOutputStream(file).use { output ->
                pdfDocument.writeTo(output)
            }

            // STEP 7: Close document
            pdfDocument.close()

            val uri = file.toUri().toString()
            Log.d("PDF_TEST", "Blank PDF created at: $uri")

            Result.success(uri)

        } catch (e: Exception) {
            Log.e("PDF_TEST", "Error creating blank PDF", e)
            Result.failure(e)
        }
    }
}
