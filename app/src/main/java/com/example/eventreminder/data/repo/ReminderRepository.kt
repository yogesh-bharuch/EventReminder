package com.example.eventreminder.data.repo

// ============================================================
// ReminderRepository — Clean Architecture (UUID Only)
//
// Responsibilities:
//  • Pure data access layer (Room only)
//  • NO scheduling, NO business logic, NO alarms
//  • Ensures DB writes are committed (read-after-write verification)
//  • Returns IDs and entities for ViewModel to process
//
// Alarm scheduling MUST be performed ONLY inside ViewModel.
// ============================================================

import android.content.Context
import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.model.EventReminder
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
    private val dao: ReminderDao
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

        // Room returns rowId: Long (ignored)
        dao.insert(updated)

        // Ensure DB commit visible before returning
        val verified = dao.getById(updated.id)
        if (verified == null) {
            Timber.tag(TAG).e("❌ Insert verification FAILED for id=${updated.id}")
        } else {
            Timber.tag(TAG).i("✔ Insert verified for id=${updated.id}")
        }

        return updated.id // ALWAYS return UUID
    }

    // ============================================================
    // UPDATE (UUID)
    // ============================================================

    suspend fun update(reminder: EventReminder) {
        Timber.tag(TAG).i("update id=${reminder.id}")

        val updated = reminder.copy(updatedAt = System.currentTimeMillis())

        dao.update(updated)

        // Ensure DB commit visible before returning
        val verified = dao.getById(updated.id)
        if (verified == null) {
            Timber.tag(TAG).e("❌ Update verification FAILED for id=${updated.id}")
        } else {
            Timber.tag(TAG).i("✔ Update verified for id=${updated.id}")
        }
    }

    // ============================================================
    // DELETE (Soft delete)
    // ============================================================

    suspend fun markDelete(reminder: EventReminder) {
        Timber.tag(TAG).i("delete id=${reminder.id}")

        val ts = System.currentTimeMillis()
        val deleted = reminder.copy(isDeleted = true, updatedAt = ts)

        dao.markDeleted(reminder.id)
        dao.update(deleted)

        // ❌ No alarm cancellation here — ViewModel handles this.
    }

    // ============================================================
    // FETCH NON-DELETED ENABLED REMINDERS
    // Used by sync + BOOT restoration
    // ============================================================

    suspend fun getNonDeletedEnabled(): List<EventReminder> {
        return getAllOnce().filter { it.enabled && !it.isDeleted }
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
