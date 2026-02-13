package com.example.eventreminder.pdf

// ============================================================
// Imports
// ============================================================
import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.logging.DEBUG_TAG
import com.example.eventreminder.sync.core.UserIdProvider
import com.example.eventreminder.util.NextOccurrenceCalculator
import timber.log.Timber
import javax.inject.Inject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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
    suspend fun loadActiveAlarms(
        includeTodayAlarms: Boolean = false
    ): List<AlarmEntry> {

        val uid = userIdProvider.getUserId()
            ?: error("❌ UID is null — cannot load alarms for logged-out user")

        val zoneId = ZoneId.systemDefault()
        val todayStart = LocalDate.now(zoneId)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        val now = Instant.now().toEpochMilli()

        val reminders = dao.getAllOnce(uid = uid)
            .filter { reminder ->
                if (includeTodayAlarms) true else reminder.enabled
            }

        val result = mutableListOf<AlarmEntry>()

        reminders.forEach { reminder ->

            val baseTriggerMs = NextOccurrenceCalculator.nextOccurrence(
                reminder.eventEpochMillis,
                reminder.timeZone,
                reminder.repeatRule
            )

            // -------------------------------------------------
            // CASE 1: Normal future occurrence
            // -------------------------------------------------
            if (baseTriggerMs != null) {

                for (offsetMillis in reminder.reminderOffsets) {

                    val triggerMs = baseTriggerMs - offsetMillis

                    val cutoff = if (includeTodayAlarms) todayStart else now

                    if (triggerMs >= cutoff) {
                        result.add(
                            AlarmEntry(
                                eventId = reminder.id,
                                eventTitle = reminder.title,
                                eventDateEpoch = reminder.eventEpochMillis,
                                nextTrigger = triggerMs,
                                offsetMinutes = offsetMillis / 60000L,
                                description = reminder.description ?: "-"
                            )
                        )
                    }
                }
            }

            // -------------------------------------------------
            // CASE 2: One-time reminder already fired today
            // (baseTriggerMs == null)
            // -------------------------------------------------
            else if (includeTodayAlarms) {

                val eventDay = Instant.ofEpochMilli(reminder.eventEpochMillis)
                    .atZone(zoneId)
                    .toLocalDate()

                val today = LocalDate.now(zoneId)

                if (eventDay == today) {

                    result.add(
                        AlarmEntry(
                            eventId = reminder.id,
                            eventTitle = reminder.title,
                            eventDateEpoch = reminder.eventEpochMillis,
                            nextTrigger = reminder.eventEpochMillis,
                            offsetMinutes = 0L,
                            description = reminder.description ?: "-"
                        )
                    )
                }
            }
        }

        return result
    }

}
