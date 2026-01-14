package com.example.eventreminder.pdf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.eventreminder.logging.DEBUG_TAG
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for generating PDFs.
 *
 * IMPORTANT:
 * - Repository decides *where* the PDF is stored.
 * - Rendering logic is delegated to generateGenericPdf().
 * - This mirrors the behavior of the working allAlarmsReport flow.
 */
@Singleton
class PdfRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun generatePdf(
        title: String,
        headers: List<String>,
        colWidths: List<Float>,
        rows: List<List<PdfCell>>,
        layout: PdfLayoutConfig,
        fileName: String
    ): Uri? {
        return try {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")

            // Create MediaStore entry under Documents
            val uri = resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                }
            ) ?: return null

            // Open OutputStream for the SAME URI that will later be opened by UI
            resolver.openOutputStream(uri)?.use { outputStream ->
                generateGenericPdf(
                    context = context,
                    title = title,
                    headers = headers,
                    colWidths = colWidths,
                    rows = rows,
                    layout = layout,
                    outputStream = outputStream
                )
            }

            Timber.tag(DEBUG_TAG).d("PDF saved → $uri [PdfRepository.kt::generatePdf]")
            uri

        } catch (e: Exception) {
            Timber.tag(DEBUG_TAG).e(e, "PDF generation failed [PdfRepository.kt::generatePdf]")
            null
        }
    }
}
