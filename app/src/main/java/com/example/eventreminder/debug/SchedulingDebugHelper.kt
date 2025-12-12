package com.example.eventreminder.debug

import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.scheduler.ReminderSchedulingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SchedulingDebug"

@Singleton
class SchedulingDebugHelper @Inject constructor(
    private val repo: ReminderRepository,
    private val engine: ReminderSchedulingEngine
) {

    private val formatter =
        DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z")

    suspend fun printAllReminderState() = withContext(Dispatchers.IO) {
        val reminders = repo.getAllOnce()

        Timber.tag(TAG).i("====== SCHEDULING DEBUG DUMP (${reminders.size} reminders) ======")

        reminders.forEach { printReminderState(it) }

        Timber.tag(TAG).i("====== END DEBUG DUMP ======")
    }

    suspend fun printReminderState(reminder: EventReminder) {
        Timber.tag(TAG).i("Reminder → ${reminder.id} (${reminder.title})")

        Timber.tag(TAG).i("Offsets: ${reminder.reminderOffsets}")
        Timber.tag(TAG).i("RepeatRule: ${reminder.repeatRule}")

        val next = engine.computeNextEvent(reminder)
        if (next == null) {
            Timber.tag(TAG).i("NextOccurrence: NONE (one-time past)")
        } else {
            Timber.tag(TAG).i(
                "NextOccurrence: ${
                    Instant.ofEpochMilli(next).atZone(ZoneId.of(reminder.timeZone))
                        .format(formatter)
                }"
            )
        }

        // Fire states
        val fireStates = repo.getAllFireStatesForReminder(reminder.id)
        if (fireStates.isEmpty()) {
            Timber.tag(TAG).i("FireStates: (none)")
        } else {
            fireStates.forEach {
                Timber.tag(TAG).i("FireState offset=${it.offsetMillis} lastFiredAt=${it.lastFiredAt}")
            }
        }
    }
}
