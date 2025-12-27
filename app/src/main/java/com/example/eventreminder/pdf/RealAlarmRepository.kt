package com.example.eventreminder.pdf

// ============================================================
// Imports
// ============================================================
import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.sync.core.UserIdProvider
import com.example.eventreminder.util.NextOccurrenceCalculator
import javax.inject.Inject
import java.time.Instant

/**
 * RealAlarmRepository
 *
 * Read-only repository used for PDF / alarm preview generation.
 *
 * Notes:
 * - UID-scoped (multi-user safe)
 * - Does NOT create or modify EventReminder
 * - Fails fast if user is not authenticated
 */
class RealAlarmRepository @Inject constructor(
    private val dao: ReminderDao,
    private val userIdProvider: UserIdProvider
) {

    /**
     * Builds a list of AlarmEntry from actual reminders in the DB.
     * Expands all offsets.
     */
    suspend fun loadActiveAlarms(): List<AlarmEntry> {

        val uid = userIdProvider.getUserId()
            ?: error("❌ UID is null — cannot load alarms for logged-out user")

        val reminders = dao.getAllOnce(uid = uid)
            .filter { it.enabled }

        val now = Instant.now().toEpochMilli()
        val result = mutableListOf<AlarmEntry>()

        reminders.forEach { reminder ->

            // Compute next local-time occurrence (epoch millis)
            val baseTriggerMs = NextOccurrenceCalculator.nextOccurrence(
                reminder.eventEpochMillis,
                reminder.timeZone,
                reminder.repeatRule
            )

            if (baseTriggerMs != null) {
                for (offsetMillis in reminder.reminderOffsets) {

                    // Apply offset: event fires earlier
                    val triggerMs = baseTriggerMs - offsetMillis

                    if (triggerMs > now) {
                        result.add(
                            AlarmEntry(
                                eventId = reminder.id,
                                eventTitle = reminder.title,
                                eventDateEpoch = reminder.eventEpochMillis, // actual event date
                                nextTrigger = triggerMs,                    // final fire time
                                offsetMinutes = offsetMillis / 60000L,
                                description = reminder.description ?: "-"
                            )
                        )
                    }
                }
            }
        }

        return result
    }
}
