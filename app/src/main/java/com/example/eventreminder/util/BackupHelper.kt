package com.example.eventreminder.util

import android.content.Context
import java.io.File

/**
 * BackupHelper handles saving and reading reminder data
 * to/from JSON files in internal storage.
 */
object BackupHelper {

    /**
     * Saves JSON into "reminders_backup.json" inside internal storage.
     *
     * @return Success message with reminder count and file name.
     */
    fun saveJsonToFile(context: Context, json: String, count: Int): String {
        val file = File(context.filesDir, "reminders_backup.json")
        file.writeText(json)
        return "Backup completed: $count reminders saved to ${file.name}"
    }

    /**
     * Reads JSON back from "reminders_backup.json".
     */
    fun readJsonFromFile(context: Context): String? {
        val file = File(context.filesDir, "reminders_backup.json")
        return if (file.exists()) file.readText() else null
    }
}