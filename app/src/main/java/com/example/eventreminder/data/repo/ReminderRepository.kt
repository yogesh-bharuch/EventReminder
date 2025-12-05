package com.example.eventreminder.data.repo

import android.content.Context
import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.scheduler.AlarmScheduler
import com.example.eventreminder.util.BackupHelper
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File


private const val TAG = "ReminderRepository"

@Singleton
class ReminderRepository @Inject constructor(
    private val dao: ReminderDao,
    private val alarmScheduler: AlarmScheduler
) {

    // ============================================================
    // Public API
    // ============================================================

    fun getAllReminders(): Flow<List<EventReminder>> =
        dao.getAll()

    suspend fun getAllOnce(): List<EventReminder> =
        dao.getAllOnce()

    suspend fun getReminder(id: Long): EventReminder? =
        dao.getById(id)

    suspend fun insert(reminder: EventReminder): Long {
        Timber.tag(TAG).i("insert id=${reminder.id}")
        val id = dao.insert(reminder)
        schedule(reminder.copy(id = id))
        return id
    }

    suspend fun update(reminder: EventReminder) {
        Timber.tag(TAG).i("update id=${reminder.id}")
        dao.update(reminder)
        reschedule(reminder)
    }

    suspend fun delete(reminder: EventReminder) {
        Timber.tag(TAG).i("delete id=${reminder.id}")
        dao.delete(reminder)
        cancel(reminder)
    }

    suspend fun deleteById(id: Long) {
        Timber.tag(TAG).i("delete id=$id")
        dao.deleteById(id)
    }

    // ============================================================
    // Internal Scheduling Helpers
    // ============================================================

    private fun schedule(reminder: EventReminder) {
        if (!reminder.enabled) return
        alarmScheduler.scheduleAll(
            reminderId = reminder.id,
            title = reminder.title,
            message = reminder.description,
            repeatRule = reminder.repeatRule,
            nextEventTime = reminder.eventEpochMillis,
            offsets = reminder.reminderOffsets
        )
    }

    private fun cancel(reminder: EventReminder) {
        alarmScheduler.cancelAll(
            reminderId = reminder.id,
            offsets = reminder.reminderOffsets
        )
    }

    private fun reschedule(reminder: EventReminder) {
        cancel(reminder)
        schedule(reminder)
    }

    suspend fun exportRemindersToJson(context: Context): String {
        val reminders = getAllOnce()
        val json = Json.encodeToString(reminders)

        // Save file and get success message with count
        val msg = BackupHelper.saveJsonToFile(context, json, reminders.size)

        Timber.tag("BACKUP").i(msg)
        return msg
    }

    suspend fun restoreRemindersFromBackup(context: Context): String {
        val file = File(context.filesDir, "reminders_backup.json")
        if (!file.exists()) {
            Timber.tag("RESTORE_REMINDERS").w("No backup file found")
            return "No backup file found"
        }

        return try {
            val json = file.readText()
            val reminders: List<EventReminder> = Json.decodeFromString(json)

            val existingReminders = getAllOnce()
            val existingMap = existingReminders.associateBy { it.id }

            var insertedCount = 0
            var updatedCount = 0
            var skippedCount = 0

            reminders.forEach { backupReminder ->
                val existing = existingMap[backupReminder.id]
                if (existing == null) {
                    insert(backupReminder)
                    insertedCount++
                } else if (existing != backupReminder) {
                    deleteById(existing.id)
                    insert(backupReminder)
                    updatedCount++
                } else {
                    skippedCount++
                }
            }

            val msg = "Restore completed: $insertedCount new, $updatedCount updated, $skippedCount skipped"
            Timber.tag("RESTORE_REMINDERS").i(msg)
            msg
        } catch (ex: Exception) {
            Timber.tag("RESTORE_REMINDERS").e(ex, "Restore failed")
            "Restore failed"
        }
    }

}
