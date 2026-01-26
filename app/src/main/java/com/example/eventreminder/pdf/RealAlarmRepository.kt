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
     * Caller:
     *  - ReminderReportDataBuilder.buildReport()
     *
     * Responsibility:
     *  - Loads all enabled reminders for the currently logged-in user.
     *  - Computes the NEXT firing time for each reminder using:
     *      - event base time
     *      - repeat rule
     *      - reminder offsets
     *  - Filters out past alarms.
     *  - Expands reminders into individual alarm entries per offset.
     *
     * Input:
     *  - None (uses UID from UserIdProvider).
     *
     * Output:
     *  - List<AlarmEntry> where each entry represents:
     *      - one reminder
     *      - one offset
     *      - one future firing time
     *
     * Ordering:
     *  - No guaranteed order (caller is responsible for sorting).
     *
     * Failure Conditions:
     *  - Throws if user is not logged in (UID is null).
     *
     * Time Semantics:
     *  - All time calculations are in epoch millis.
     *  - Uses NextOccurrenceCalculator as the single recurrence engine.
     *
     * Side Effects:
     *  - None (read-only database access).
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
