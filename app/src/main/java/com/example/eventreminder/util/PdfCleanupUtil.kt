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
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH
        )

        val selection = """
            ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?
            OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?
            OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?
            OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?
        """.trimIndent()

        val args = arrayOf(
            "%7_Days%",        // Next7Days
            "%Report_All%",   // All alarms report
            "%Contacts%",     // Contacts pdf
            "%Reminders%"     // Reminders pdf
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

                // Safety guard → only Documents
                if (path != Environment.DIRECTORY_DOCUMENTS + "/") continue

                val uri = Uri.withAppendedPath(collection, id.toString())
                val rows = resolver.delete(uri, null, null)

                if (rows > 0) {
                    deletedCount++
                    Timber.tag(CLEANUP_PDF_TAG).d("PDF_CLEANUP deleted name=$name path=$path uri=$uri [PdfCleanupUtil.kt::cleanupGeneratedPdfs]")
                }
            }
        }

        Timber.tag(CLEANUP_PDF_TAG).d("PDF_CLEANUP_COMPLETE total=$deletedCount [PdfCleanupUtil.kt::cleanupGeneratedPdfs]")
    }
}
