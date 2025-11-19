package com.example.eventreminder.pdf

import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.util.NextOccurrenceCalculator
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class RealAlarmRepository @Inject constructor(
    private val dao: ReminderDao
) {

    /**
     * Builds a list of AlarmEntry from actual reminders in the DB.
     * Expands all offsets.
     *
     * AlarmEntry requires:
     *  - nextTrigger: Long (epoch millis)
     *  - offsetMinutes: Long
     */
    suspend fun loadActiveAlarms(): List<AlarmEntry> {

        val reminders = dao.getAllOnce()
            .filter { it.enabled } // Only enabled ones

        val now = Instant.now().toEpochMilli()

        val result = mutableListOf<AlarmEntry>()

        reminders.forEach { reminder ->

            // Base local-time next occurrence
            val baseTriggerMs = NextOccurrenceCalculator.nextOccurrence(
                reminder.eventEpochMillis,
                reminder.timeZone,
                reminder.repeatRule
            )

            if (baseTriggerMs != null) {

                // Expand each reminder offset
                for (offsetMillis in reminder.reminderOffsets) {

                    val triggerMs = baseTriggerMs - offsetMillis

                    if (triggerMs > now) {
                        result.add(
                            AlarmEntry(
                                eventId = reminder.id,
                                eventTitle = reminder.title,
                                nextTrigger = triggerMs,                        // ✔ LONG
                                offsetMinutes = offsetMillis / 60_000L         // ✔ LONG
                            )
                        )
                    }
                }
            }
        }

        return result
    }
}
