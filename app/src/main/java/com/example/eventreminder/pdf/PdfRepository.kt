package com.example.eventreminder.pdf

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.eventreminder.logging.DEBUG_TAG
import com.example.eventreminder.logging.SHARE_PDF_TAG
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import android.content.ContentResolver


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
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

            // deletes old reports files,
            //deleteAllNext7DaysPdfs()
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

            Timber.tag(SHARE_PDF_TAG).d("PDF saved → $uri [PdfRepository.kt::generatePdf]")
            uri

        } catch (e: Exception) {
            Timber.tag(SHARE_PDF_TAG).e(e, "PDF generation failed [PdfRepository.kt::generatePdf]")
            null
        }
    }
    // =========================================================
    // MediaStore overwrite helper
    // =========================================================
    private fun deleteAllNext7DaysPdfs() {

        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH
        )

        var deletedCount = 0

        resolver.query(collection, projection, null, null, null)?.use { cursor ->

            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

            while (cursor.moveToNext()) {

                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex)
                val path = cursor.getString(pathIndex)

                val nameLower = name.lowercase()

                val shouldDelete =
                    nameLower.contains("7_days") ||
                            nameLower.contains("next_7") ||
                            nameLower.contains("report") ||
                            nameLower.contains("reminder") ||
                            nameLower.contains("contact")

                if (!shouldDelete) continue

                val uri = Uri.withAppendedPath(collection, id.toString())

                val rows = resolver.delete(uri, null, null)

                if (rows > 0) {
                    deletedCount++
                    Timber.tag(SHARE_PDF_TAG).d(
                        "PDF_DELETED name=$name path=$path uri=$uri [PdfRepository.kt::deleteAllNext7DaysPdfs]"
                    )
                }
            }
        }

        Timber.tag(SHARE_PDF_TAG).d(
            "PDF_BULK_DELETE_COMPLETE total=$deletedCount [PdfRepository.kt::deleteAllNext7DaysPdfs]"
        )
    }

}
