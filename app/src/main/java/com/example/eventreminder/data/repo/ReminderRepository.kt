package com.example.eventreminder.data.repo

import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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
}
