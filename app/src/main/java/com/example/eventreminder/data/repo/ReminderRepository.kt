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
        val newReminder = reminder.copy(updatedAt = System.currentTimeMillis())
        val id = dao.insert(newReminder)
        schedule(newReminder.copy(id = id))
        return id
    }

    suspend fun update(reminder: EventReminder) {
        Timber.tag(TAG).i("update id=${reminder.id}")
        val updatedReminder = reminder.copy(updatedAt = System.currentTimeMillis())
        dao.update(updatedReminder)
        reschedule(updatedReminder)
    }

    suspend fun markDelete(reminder: EventReminder) {
        Timber.tag(TAG).i("delete id=${reminder.id}")
        val ts = System.currentTimeMillis()

        // Build deleted model once
        val deleted = reminder.copy(isDeleted = true, updatedAt = ts)

        // Apply soft-delete flag in DB
        dao.markDeleted(reminder.id)
        dao.update(deleted)

        // Cancel alarms using UPDATED object
        cancel(deleted)
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
            val backupList: List<EventReminder> = Json.decodeFromString(json)

            val existingList = getAllOnce()
            val existingMap = existingList.associateBy { it.id }

            var inserted = 0
            var updated = 0
            var skipped = 0

            backupList.forEach { backup ->

                // ⚠ Skip deleted reminders in backup
                if (backup.isDeleted) {
                    Timber.tag("RESTORE_REMINDERS").i("Skipping deleted reminder id=${backup.id}")
                    skipped++
                    return@forEach
                }

                val existing = existingMap[backup.id]

                if (existing == null) {
                    // -------------------------------------------
                    // CASE 1: New reminder → Insert fresh copy
                    // -------------------------------------------
                    val clean = backup.copy(
                        id = 0L,
                        isDeleted = false,
                        updatedAt = System.currentTimeMillis()
                    )
                    insert(clean)
                    inserted++
                } else {

                    // -------------------------------------------
                    // CASE 2: Existing reminder → Compare content
                    // Ignore updatedAt & isDeleted when comparing
                    // -------------------------------------------
                    val equivalent =
                        existing.title == backup.title &&
                                existing.description == backup.description &&
                                existing.eventEpochMillis == backup.eventEpochMillis &&
                                existing.timeZone == backup.timeZone &&
                                existing.repeatRule == backup.repeatRule &&
                                existing.reminderOffsets == backup.reminderOffsets &&
                                existing.enabled == backup.enabled &&
                                existing.backgroundUri == backup.backgroundUri

                    if (equivalent) {
                        skipped++
                    } else {
                        // Update the existing row
                        val merged = backup.copy(
                            id = existing.id,
                            isDeleted = false,
                            updatedAt = System.currentTimeMillis()
                        )
                        update(merged)
                        updated++
                    }
                }
            }

            val msg = "Restore completed: $inserted new, $updated updated, $skipped skipped"
            Timber.tag("RESTORE_REMINDERS").i(msg)
            msg

        } catch (ex: Exception) {
            Timber.tag("RESTORE_REMINDERS").e(ex, "Restore failed")
            "Restore failed"
        }
    }

}
