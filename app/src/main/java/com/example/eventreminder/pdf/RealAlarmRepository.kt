package com.example.eventreminder.pdf

import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.util.NextOccurrenceCalculator
import javax.inject.Inject
import java.time.Instant

class RealAlarmRepository @Inject constructor(
    private val dao: ReminderDao
) {

    /**
     * Builds a list of AlarmEntry from actual reminders in the DB.
     * Expands all offsets.
     */
    suspend fun loadActiveAlarms(): List<AlarmEntry> {

        val reminders = dao.getAllOnce()
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
                                eventDateEpoch = reminder.eventEpochMillis,   // actual event date
                                nextTrigger = triggerMs,                      // final fire time
                                offsetMinutes = offsetMillis / 60000L,         // <-- FIXED (Long)
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
