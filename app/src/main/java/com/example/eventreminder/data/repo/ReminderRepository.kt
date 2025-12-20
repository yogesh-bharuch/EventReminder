package com.example.eventreminder.data.repo

/*// ============================================================
// ReminderRepository — Clean Architecture (UUID Only)
// Responsibilities:
//  • Pure data access layer (Room only)
//  • NO scheduling, NO business logic, NO alarms
//  • Ensures DB writes are committed (read-after-write verification)
//  • Returns IDs and entities for ViewModel to process
//
// This update adds per-offset fire-state helpers (lastFiredAt storage).
// ============================================================*/

import android.content.Context
import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.local.ReminderFireStateDao
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.model.ReminderFireStateEntity
import com.example.eventreminder.util.BackupHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.example.eventreminder.logging.DELETE_TAG

private const val TAG = "ReminderRepository"


@Singleton
class ReminderRepository @Inject constructor(
    private val dao: ReminderDao,
    private val fireStateDao: ReminderFireStateDao
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
    // FIRE STATE HELPERS (Per-offset lastFiredAt)
    // ============================================================
    suspend fun getLastFiredAt(reminderId: String, offsetMillis: Long): Long? =
        fireStateDao.getLastFiredAt(reminderId, offsetMillis)

    suspend fun upsertLastFiredAt(reminderId: String, offsetMillis: Long, ts: Long) {
        val entity = ReminderFireStateEntity(
            reminderId = reminderId,
            offsetMillis = offsetMillis,
            lastFiredAt = ts
        )
        fireStateDao.upsert(entity)
        Timber.tag(TAG).d("Upserted FireState → id=$reminderId offset=$offsetMillis ts=$ts")
    }

    suspend fun getAllFireStatesForReminder(reminderId: String) =
        fireStateDao.getAllForReminder(reminderId)

    suspend fun deleteFireStatesForReminder(reminderId: String) =
        fireStateDao.deleteForReminder(reminderId)

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
    // DELETE (Soft delete — SINGLE SOURCE OF TRUTH)
    // ============================================================
    suspend fun markDelete(reminder: EventReminder) {
        Timber.tag(DELETE_TAG).d("🟥 markDelete() START id=${reminder.id}")

        val ts = System.currentTimeMillis()

        val tombstone = reminder.copy(
            isDeleted = true,
            updatedAt = ts
        )

        Timber.tag(DELETE_TAG).d("🪦 Tombstone → id=${tombstone.id}, isDeleted=true, updatedAt=$ts")

        try {
            // ✅ SINGLE atomic write
            dao.update(tombstone)

            Timber.tag(DELETE_TAG).i("✔ Tombstone created id=${tombstone.id} updatedAt=$ts")
        } catch (t: Throwable) {
            Timber.tag(DELETE_TAG).e(t, "❌ markDelete FAILED id=${reminder.id}")
            throw t
        }

        Timber.tag(DELETE_TAG).d("🟥 markDelete() END id=${reminder.id}")
    }

    // ---------------------------------------------------------
    // To clean up db, One-time DB normalization from "" to null
    // ---------------------------------------------------------
    suspend fun normalizeRepeatRules() {
        dao.normalizeRepeatRule()
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

    // ============================================================
    // UPDATE (Scheduler / Lifecycle support)
    // ============================================================
    suspend fun updateEnabled(
        id: String,
        enabled: Boolean,
        isDeleted: Boolean,
        updatedAt: Long
    ) {
        Timber.tag(TAG).i("updateEnabled() id=$id enabled=$enabled updatedAt=$updatedAt")

        dao.updateEnabled(
            id = id,
            enabled = enabled,
            isDeleted = isDeleted,
            updatedAt = updatedAt
        )

        // Optional verification (matches your repo style)
        val verified = dao.getById(id)
        if (verified == null) {
            Timber.tag(TAG).e("❌ updateEnabled verification FAILED id=$id")
        } else {
            Timber.tag(TAG).i("✔ updateEnabled verified id=$id enabled=${verified.enabled}")
        }
    }

    // ============================================================
    // GARBAGE COLLECTION (Tombstones)
    // ============================================================

    /**
     * Returns tombstone reminders older than the given cutoff time.
     *
     * Used by manual tombstone GC only.
     */
    suspend fun getDeletedBefore(cutoffEpochMillis: Long): List<EventReminder> {

        Timber.tag(TAG).d("getDeletedBefore() cutoffEpochMillis=$cutoffEpochMillis")

        return dao.getDeletedBefore(cutoffEpochMillis = cutoffEpochMillis)
    }

    /**
     * Permanently deletes reminders by UUID.
     *
     * ⚠️ Destructive — used only by manual tombstone GC.
     */
    suspend fun hardDeleteByIds(ids: List<String>) {
        Timber.tag(TAG).w("hardDeleteByIds() count=${ids.size}")

        if (ids.isEmpty()) return

        dao.hardDeleteByIds(ids = ids)
    }



}
