package com.example.eventreminder.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.example.eventreminder.logging.CLEANUP_PDF_TAG
import timber.log.Timber

object PdfCleanupUtil {

    fun cleanupGeneratedPdfs(context: Context) {

        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH
        )

        val selection = """
        ${MediaStore.MediaColumns.MIME_TYPE} = ?
        AND (
            LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE ?
            OR LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE ?
            OR LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE ?
            OR LOWER(${MediaStore.MediaColumns.DISPLAY_NAME}) LIKE ?
        )
    """.trimIndent()

        val args = arrayOf(
            "application/pdf",
            "%7_daysre%",
            "%report_all%",
            "%contacts%",
            "%reminders%"
        )

        var deletedCount = 0

        resolver.query(collection, projection, selection, args, null)?.use { cursor ->

            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)

            while (cursor.moveToNext()) {

                val id = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex)
                val path = cursor.getString(pathIndex)

                // ✅ Scoped storage safe guard
                if (!path.startsWith(Environment.DIRECTORY_DOCUMENTS)) continue

                val uri = Uri.withAppendedPath(collection, id.toString())
                val rows = resolver.delete(uri, null, null)

                if (rows > 0) {
                    deletedCount++
                    Timber.tag(CLEANUP_PDF_TAG).d("PDF_CLEANUP deleted name=$name path=$path uri=$uri " + "[PdfCleanupUtil.kt::cleanupGeneratedPdfs]")
                }
            }
        }

        Timber.tag(CLEANUP_PDF_TAG).d("PDF_CLEANUP_COMPLETE total=$deletedCount [PdfCleanupUtil.kt::cleanupGeneratedPdfs]")
    }

}
