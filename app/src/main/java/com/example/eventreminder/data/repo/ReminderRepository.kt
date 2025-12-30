package com.example.eventreminder.data.repo

// ============================================================
// Imports
// ============================================================
import android.content.Context
import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.local.ReminderFireStateDao
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.model.ReminderFireStateEntity
import com.example.eventreminder.sync.core.UserIdProvider
import com.example.eventreminder.util.BackupHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.example.eventreminder.logging.DELETE_TAG

private const val TAG = "ReminderRepository"

/**
 * ReminderRepository
 *
 * UID-scoped Room-only repository.
 *
 * Rules:
 * - Firebase UID is mandatory for all operations
 * - UID is obtained ONLY via UserIdProvider
 * - If UID is null → fail fast
 * - ViewModels remain UID-agnostic
 * - No SyncEngine or auth redesign
 */
@Singleton
class ReminderRepository @Inject constructor(
    private val dao: ReminderDao,
    private val fireStateDao: ReminderFireStateDao,
    private val userIdProvider: UserIdProvider
) {

    // ============================================================
    // UID helper (FAIL-FAST)
    // ============================================================

    private suspend fun requireUid(): String {
        return userIdProvider.getUserId()
            ?: error("❌ UID is null — user is logged out or auth not ready")
    }

    // ============================================================
    // READ API (UID-SCOPED)
    // ============================================================

    /**
     * UID-agnostic Flow for UI layer.
     *
     * ViewModels must call THIS method.
     */
    fun getAllReminders(): Flow<List<EventReminder>> = flow {
        val uid = requireUid()
        emitAll(dao.getAll(uid = uid))
    }

    /**
     * Explicit UID version (internal / special cases only).
     */
    fun getAllReminders(uid: String): Flow<List<EventReminder>> =
        dao.getAll(uid = uid)

    suspend fun getAllOnce(): List<EventReminder> =
        dao.getAllOnce(uid = requireUid())

    suspend fun getReminder(id: String): EventReminder? =
        dao.getById(uid = requireUid(), id = id)

    // ============================================================
    // FIRE STATE HELPERS
    // ============================================================

    /**
     * Called by:
     * - ReminderSchedulingEngine (boot restore, missed detection)
     *
     * Returns:
     * - last fired timestamp for reminder+offset or null
     */
    suspend fun getLastFiredAt(
        reminderId: String,
        offsetMillis: Long
    ): Long? =
        fireStateDao.getLastFiredAt(reminderId, offsetMillis)

    /**
     * Called by:
     * - ReminderSchedulingEngine (on fire / immediate fire)
     *
     * Responsibility:
     * - Persist notification fire time per offset
     */
    suspend fun upsertLastFiredAt(
        reminderId: String,
        offsetMillis: Long,
        ts: Long
    ) {
        Timber.tag(TAG).e(
            "FIRESTATE_UPSERT → id=%s offset=%d firedAt=%d [ReminderRepository.kt::upsertLastFiredAt]",
            reminderId,
            offsetMillis,
            ts
        )

        fireStateDao.upsert(
            ReminderFireStateEntity(
                reminderId = reminderId,
                offsetMillis = offsetMillis,
                lastFiredAt = ts
            )
        )

        Timber.tag(TAG).d(
            "FireState upserted → id=$reminderId offset=$offsetMillis ts=$ts [ReminderRepository.kt::upsertLastFiredAt]"
        )
    }

    /**
     * Called by:
     * - ReminderReceiver (ACTION_DISMISS)
     *
     * Responsibility:
     * - Record manual user dismissal for a specific reminder offset
     *
     * Notes:
     * - Fire ≠ Dismiss
     * - Does NOT disable, delete, or reschedule anything
     * - Safe no-op if fire-state row does not exist
     */
    suspend fun recordDismissed(
        reminderId: String,
        offsetMillis: Long,
        dismissedAt: Long = System.currentTimeMillis()
    ) {
        fireStateDao.updateDismissedAt(
            id = reminderId,
            offsetMillis = offsetMillis,
            dismissedAt = dismissedAt
        )

        Timber.tag(TAG).i(
            "Dismiss recorded → id=$reminderId offset=$offsetMillis at=$dismissedAt [ReminderRepository.kt::recordDismissed]"
        )
    }

    suspend fun deleteFireStatesForReminder(reminderId: String) =
        fireStateDao.deleteForReminder(reminderId)

    // ============================================================
    // INSERT (UID ENFORCED)
    // ============================================================

    suspend fun insert(reminder: EventReminder): String {
        val uid = requireUid()

        val updated = reminder.copy(
            uid = uid,
            updatedAt = System.currentTimeMillis()
        )

        Timber.tag(TAG).i("insert id=${updated.id} uid=$uid [ReminderRepository.kt::insert]")

        dao.insert(updated)
        return updated.id
    }

    // ============================================================
    // UPDATE (UID ENFORCED)
    // ============================================================

    suspend fun update(reminder: EventReminder) {
        val uid = requireUid()

        dao.update(
            reminder.copy(
                uid = uid,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    // ============================================================
    // DELETE (SOFT DELETE / TOMBSTONE)
    // ============================================================

    suspend fun markDelete(reminder: EventReminder) {
        val uid = requireUid()
        val ts = System.currentTimeMillis()

        Timber.tag(DELETE_TAG).d(
            "🪦 Tombstone → id=${reminder.id} uid=$uid [ReminderRepository.kt::markDelete]"
        )

        dao.update(
            reminder.copy(
                uid = uid,
                isDeleted = true,
                updatedAt = ts
            )
        )
    }

    // ============================================================
    // NORMALIZATION
    // ============================================================

    suspend fun normalizeRepeatRules() {
        dao.normalizeRepeatRule(uid = requireUid())
    }

    // ============================================================
    // ENABLE / DISABLE (Scheduler support)
    // ============================================================

    suspend fun updateEnabled(
        id: String,
        enabled: Boolean,
        isDeleted: Boolean,
        updatedAt: Long
    ) {
        dao.updateEnabled(
            uid = requireUid(),
            id = id,
            enabled = enabled,
            isDeleted = isDeleted,
            updatedAt = updatedAt
        )
    }

    // ============================================================
    // GARBAGE COLLECTION (TOMBSTONES)
    // ============================================================

    suspend fun getDeletedBefore(cutoffEpochMillis: Long): List<EventReminder> =
        dao.getDeletedBefore(
            uid = requireUid(),
            cutoffEpochMillis = cutoffEpochMillis
        )

    suspend fun getNonDeletedEnabled(): List<EventReminder> =
        dao.getAllOnce(uid = requireUid())
            .filter { it.enabled && !it.isDeleted }

    suspend fun hardDeleteByIds(ids: List<String>) {
        if (ids.isEmpty()) return
        dao.hardDeleteByIds(uid = requireUid(), ids = ids)
    }

    // ============================================================
    // BACKUP EXPORT / RESTORE
    // ============================================================

    suspend fun exportRemindersToJson(context: Context): String {
        val reminders = getAllOnce()
        val json = Json.encodeToString(reminders)

        val msg = BackupHelper.saveJsonToFile(context, json, reminders.size)
        Timber.tag("BACKUP").i(msg)
        return msg
    }

    suspend fun restoreRemindersFromBackup(context: Context): String {
        val uid = requireUid()
        val file = File(context.filesDir, "reminders_backup.json")
        if (!file.exists()) return "No backup file found"

        val json = file.readText()
        val backupList: List<EventReminder> = Json.decodeFromString(json)

        backupList.forEach { reminder ->
            if (!reminder.isDeleted) {
                insert(
                    reminder.copy(
                        uid = uid,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        return "Restore completed"
    }

    // ============================================================
    // FIRE STATE DEBUG HELPERS
    // ============================================================

    suspend fun getAllFireStatesForReminder(
        reminderId: String
    ): List<ReminderFireStateEntity> {
        return fireStateDao.getAllForReminder(reminderId)
    }
}
