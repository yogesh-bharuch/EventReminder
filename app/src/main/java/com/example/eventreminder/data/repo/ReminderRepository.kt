package com.example.eventreminder.data.repo

import android.content.Context
import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.scheduler.AlarmScheduler
import com.example.eventreminder.util.BackupHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReminderRepository"

@Singleton
class ReminderRepository @Inject constructor(
    private val dao: ReminderDao,
    private val alarmScheduler: AlarmScheduler
) {

    // ============================================================
    // READ API (UUID Only)
    // ============================================================

    fun getAllReminders(): Flow<List<EventReminder>> =
        dao.getAll()

    suspend fun getAllOnce(): List<EventReminder> =
        dao.getAllOnce()

    suspend fun getReminder(id: String): EventReminder? =
        dao.getById(id)

    // ============================================================
    // INSERT (UUID)
    // ============================================================

    suspend fun insert(reminder: EventReminder): String {
        Timber.tag(TAG).i("insert id=${reminder.id}")

        val updated = reminder.copy(updatedAt = System.currentTimeMillis())

        dao.insert(updated)
        schedule(updated)

        return updated.id
    }

    // ============================================================
    // UPDATE (UUID)
    // ============================================================

    suspend fun update(reminder: EventReminder) {
        Timber.tag(TAG).i("update id=${reminder.id}")

        val updated = reminder.copy(updatedAt = System.currentTimeMillis())
        dao.update(updated)
        reschedule(updated)
    }

    // ============================================================
    // DELETE (UUID)
    // ============================================================

    suspend fun markDelete(reminder: EventReminder) {
        Timber.tag(TAG).i("delete id=${reminder.id}")

        val ts = System.currentTimeMillis()
        val deleted = reminder.copy(isDeleted = true, updatedAt = ts)

        dao.markDeleted(reminder.id)
        dao.update(deleted)

        cancel(deleted)
    }

    // ============================================================
    // INTERNAL SCHEDULING HELPERS (UUID)
    // ============================================================

    private fun schedule(reminder: EventReminder) {
        if (!reminder.enabled) return

        alarmScheduler.scheduleAllByString(
            reminderIdString = reminder.id,
            title = reminder.title,
            message = reminder.description ?: "",
            repeatRule = reminder.repeatRule,
            nextEventTime = reminder.eventEpochMillis,
            offsets = reminder.reminderOffsets
        )
    }

    private fun cancel(reminder: EventReminder) {
        alarmScheduler.cancelAllByString(
            reminderIdString = reminder.id,
            offsets = reminder.reminderOffsets
        )
    }

    private fun reschedule(reminder: EventReminder) {
        cancel(reminder)
        schedule(reminder)
    }

    // ============================================================
    // RESCHEDULE ALL AFTER SYNC (UUID)
    // ============================================================
    /**
     * Called after Firestore Sync — ensures all alarms match the latest state.
     * Equivalent behavior to BootReceiver restore, without reboot.
     */
    suspend fun rescheduleAllAfterSync() {
        Timber.tag(TAG).i("Rescheduling ALL reminders after sync…")

        val reminders = getAllOnce()

        reminders.forEach { reminder ->

            // Skip disabled or deleted reminders
            if (!reminder.enabled || reminder.isDeleted) {
                Timber.tag(TAG).d("Skip disabled/deleted → id=${reminder.id}")
                return@forEach
            }

            // Cancel OLD alarms
            cancel(reminder)

            // Schedule NEW alarms using current fields
            schedule(reminder)

            Timber.tag(TAG).d("Re-scheduled UUID reminder → id=${reminder.id}")
        }

        Timber.tag(TAG).i("Reschedule-all complete → ${reminders.size} processed")
    }

    // ============================================================
    // BACKUP JSON EXPORT
    // ============================================================

    suspend fun exportRemindersToJson(context: Context): String {
        val reminders = getAllOnce()
        val json = Json.encodeToString(reminders)

        val msg = BackupHelper.saveJsonToFile(context, json, reminders.size)
        Timber.tag("BACKUP").i(msg)
        return msg
    }

    // ============================================================
    // BACKUP RESTORE (UUID)
    // ============================================================

    suspend fun restoreRemindersFromBackup(context: Context): String {
        val file = File(context.filesDir, "reminders_backup.json")
        if (!file.exists()) return "No backup file found"

        return try {
            val json = file.readText()
            val backupList: List<EventReminder> = Json.decodeFromString(json)

            val existing = getAllOnce().associateBy { it.id }

            var inserted = 0
            var updated = 0
            var skipped = 0

            backupList.forEach { backup ->

                if (backup.isDeleted) { skipped++; return@forEach }

                val match = existing[backup.id]

                if (match == null) {
                    val restored = backup.copy(
                        isDeleted = false,
                        updatedAt = System.currentTimeMillis()
                    )
                    insert(restored)
                    inserted++

                } else {
                    val equivalent =
                        match.title == backup.title &&
                                match.description == backup.description &&
                                match.eventEpochMillis == backup.eventEpochMillis &&
                                match.timeZone == backup.timeZone &&
                                match.repeatRule == backup.repeatRule &&
                                match.reminderOffsets == backup.reminderOffsets &&
                                match.enabled == backup.enabled &&
                                match.backgroundUri == backup.backgroundUri

                    if (equivalent) {
                        skipped++
                    } else {
                        val merged = backup.copy(
                            id = match.id,
                            isDeleted = false,
                            updatedAt = System.currentTimeMillis()
                        )
                        update(merged)
                        updated++
                    }
                }
            }

            "Restore completed: $inserted new, $updated updated, $skipped skipped"

        } catch (e: Exception) {
            Timber.tag("RESTORE_REMINDERS").e(e)
            "Restore failed"
        }
    }
}
