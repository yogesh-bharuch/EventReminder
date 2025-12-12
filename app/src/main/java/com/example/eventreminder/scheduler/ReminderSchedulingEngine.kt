package com.example.eventreminder.scheduler

// =============================================================
// ReminderSchedulingEngine — Master Scheduling Engine (UUID-only)
// Single source-of-truth for scheduling, missed-fire handling,
// per-offset lastFiredAt bookkeeping, cancel/reschedule, and
// repeat rescheduling.
//
// Responsibilities:
//  - processSavedReminder(reminder)
//  - processBootRestore(reminder, nowEpochMillis)
//  - processRepeatTrigger(reminder)
//  - processDelete(reminder)
//
// Project standards followed: Hilt, Timber TAG, named args,
// JavaDoc-style comments, section headers, and inline comments.
// =============================================================

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.scheduler.AlarmScheduler
import com.example.eventreminder.util.NextOccurrenceCalculator
import com.example.eventreminder.util.NotificationHelper
import java.time.Instant

private const val TAG = "ReminderSchedulingEngine"

/**
 * Master scheduling engine.
 * - Stateless: relies on injected repo + scheduler + helpers.
 * - Centralizes all scheduling concerns, including fire-state updates.
 */
@Singleton
class ReminderSchedulingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ReminderRepository,
    private val alarmScheduler: AlarmScheduler
) {

    // =============================================================
    // Public API
    // =============================================================

    suspend fun processSavedReminder(reminder: EventReminder) {
        Timber.tag(TAG).d("processSavedReminder: %s", reminder.id)

        cancelOffsets(reminder = reminder)

        if (!reminder.enabled) {
            Timber.tag(TAG).d("Reminder %s disabled — clearing fire-states and returning", reminder.id)
            try {
                repo.deleteFireStatesForReminder(reminder.id)
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Failed to delete fire-states for ${reminder.id}")
            }
            return
        }

        val nextEventEpochMillis = computeNextEvent(reminder = reminder)

        if (nextEventEpochMillis == null) {
            Timber.tag(TAG).d("No next occurrence for %s — nothing to schedule", reminder.id)
            return
        }

        scheduleOffsets(
            reminder = reminder,
            occurrenceEpochMillis = nextEventEpochMillis
        )
    }

    // =============================================================
    // Boot Restore
    // =============================================================

    suspend fun processBootRestore(reminder: EventReminder, nowEpochMillis: Long) {
        Timber.tag(TAG).d("processBootRestore: %s at %d", reminder.id, nowEpochMillis)

        if (!reminder.enabled) {
            Timber.tag(TAG).d("Reminder %s disabled on boot — skipping", reminder.id)
            return
        }

        val offsets = reminder.reminderOffsets.ifEmpty { listOf(0L) }

        val nextEventEpochMillis: Long? =
            if (reminder.repeatRule.isNullOrEmpty()) reminder.eventEpochMillis
            else NextOccurrenceCalculator.nextOccurrence(
                eventEpochMillis = reminder.eventEpochMillis,
                zoneIdStr = reminder.timeZone,
                repeatRule = reminder.repeatRule
            )

        // FIX #2 — use suspend-friendly loop
        for (offsetMillis in offsets) {

            val scheduledTrigger =
                (nextEventEpochMillis ?: reminder.eventEpochMillis) - offsetMillis

            try {
                val lastFiredAt = repo.getLastFiredAt(reminder.id, offsetMillis)
                val hasAlreadyFired = lastFiredAt != null && lastFiredAt >= scheduledTrigger
                val isMissed = scheduledTrigger < nowEpochMillis

                if (isMissed && !hasAlreadyFired) {
                    Timber.tag(TAG).w(
                        "Boot: Missed alarm for %s offset=%d — firing now",
                        reminder.id, offsetMillis
                    )

                    // FIRE IMMEDIATELY
                    fireNotificationNow(reminder = reminder, offsetMillis = offsetMillis)

                    try {
                        repo.upsertLastFiredAt(
                            reminderId = reminder.id,
                            offsetMillis = offsetMillis,
                            ts = System.currentTimeMillis()
                        )
                    } catch (t: Throwable) {
                        Timber.tag(TAG).e(
                            t,
                            "Failed to upsert lastFiredAt after boot-fire for ${reminder.id} offset=$offsetMillis"
                        )
                    }

                } else {
                    if (scheduledTrigger > nowEpochMillis) {
                        Timber.tag(TAG).d(
                            "Boot: Scheduling %s offset=%d at %d",
                            reminder.id,
                            offsetMillis,
                            scheduledTrigger
                        )
                        alarmScheduler.scheduleExactByString(
                            reminderIdString = reminder.id,
                            eventTriggerMillis = (nextEventEpochMillis ?: reminder.eventEpochMillis),
                            offsetMillis = offsetMillis,
                            title = reminder.title,
                            message = reminder.description ?: "",
                            repeatRule = reminder.repeatRule
                        )
                    } else {
                        Timber.tag(TAG).d(
                            "Boot: No schedule (past or already fired) for %s offset=%d",
                            reminder.id, offsetMillis
                        )
                    }
                }

            } catch (t: Throwable) {
                Timber.tag(TAG).e(
                    t,
                    "Error during boot processing for ${reminder.id} offset=$offsetMillis"
                )
            }
        }
    }

    // =============================================================
    // Repeat Trigger
    // =============================================================

    suspend fun processRepeatTrigger(reminderId: String) {
        Timber.tag(TAG).d("processRepeatTrigger: %s", reminderId)

        val reminder = try {
            repo.getReminder(reminderId) ?: run {
                Timber.tag(TAG).w("processRepeatTrigger: reminder not found %s", reminderId)
                return
            }
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to load reminder %s for repeat processing", reminderId)
            return
        }

        if (!reminder.enabled) {
            cancelOffsets(reminder = reminder)
            return
        }

        val next = computeNextEvent(reminder = reminder)
        if (next == null) {
            cancelOffsets(reminder = reminder)
            return
        }

        scheduleOffsets(reminder = reminder, occurrenceEpochMillis = next)
    }

    // =============================================================
    // Delete Reminder
    // =============================================================

    suspend fun processDelete(reminder: EventReminder) {
        Timber.tag(TAG).d("processDelete: %s", reminder.id)
        try {
            cancelOffsets(reminder = reminder)
            repo.deleteFireStatesForReminder(reminder.id)
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to delete reminder fire-states for ${reminder.id}")
        }
    }

    // =============================================================
    // Internal Helpers
    // =============================================================

    internal fun computeNextEvent(reminder: EventReminder): Long? {
        Timber.tag(TAG).d("computeNextEvent for %s", reminder.id)
        return try {
            NextOccurrenceCalculator.nextOccurrence(
                eventEpochMillis = reminder.eventEpochMillis,
                zoneIdStr = reminder.timeZone,
                repeatRule = reminder.repeatRule
            )
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "nextOccurrence calculation failed for ${reminder.id}")
            null
        }
    }

    internal fun cancelOffsets(reminder: EventReminder) {
        Timber.tag(TAG).d("cancelOffsets for %s", reminder.id)
        try {
            val offsets = reminder.reminderOffsets.ifEmpty { listOf(0L) }
            alarmScheduler.cancelAllByString(
                reminderIdString = reminder.id,
                offsets = offsets
            )
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to cancel offsets for ${reminder.id}")
        }
    }

    internal fun scheduleOffsets(reminder: EventReminder, occurrenceEpochMillis: Long) {
        val now = Instant.now().toEpochMilli()
        val offsets = reminder.reminderOffsets.ifEmpty { listOf(0L) }

        val futureOffsets = offsets.filter { offset ->
            (occurrenceEpochMillis - offset) > now
        }

        if (futureOffsets.isEmpty()) {
            Timber.tag(TAG).d("No future offsets to schedule for %s", reminder.id)
            return
        }

        Timber.tag(TAG).d(
            "Scheduling %d offsets for %s at occurrence=%d",
            futureOffsets.size, reminder.id, occurrenceEpochMillis
        )

        try {
            alarmScheduler.scheduleAllByString(
                reminderIdString = reminder.id,
                title = reminder.title,
                message = reminder.description ?: "",
                repeatRule = reminder.repeatRule,
                nextEventTime = occurrenceEpochMillis,
                offsets = futureOffsets
            )
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to schedule offsets for ${reminder.id}")
        }
    }

    // =============================================================
    // fireNotificationNow (FIX #1 — suspend fun)
    // =============================================================

    internal suspend fun fireNotificationNow(reminder: EventReminder, offsetMillis: Long) {
        Timber.tag(TAG).d(
            "fireNotificationNow for %s offset=%d",
            reminder.id, offsetMillis
        )

        try {
            val raw = reminder.id.hashCode() xor offsetMillis.hashCode()
            val notificationId =
                if (raw == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(raw)

            NotificationHelper.showNotification(
                context = context,
                notificationId = notificationId,
                title = reminder.title,
                message = reminder.description ?: "",
                eventType = inferEventType(
                    reminder.title,
                    reminder.description ?: ""
                ),
                extras = mapOf(
                    com.example.eventreminder.receivers.ReminderReceiver.EXTRA_REMINDER_ID_STRING to reminder.id,
                    com.example.eventreminder.receivers.ReminderReceiver.EXTRA_FROM_NOTIFICATION to true
                )
            )

            repo.upsertLastFiredAt(
                reminderId = reminder.id,
                offsetMillis = offsetMillis,
                ts = System.currentTimeMillis()
            )

        } catch (t: Throwable) {
            Timber.tag(TAG).e(
                t,
                "Failed to fire notification now for ${reminder.id} offset=$offsetMillis"
            )
        }
    }

    private fun inferEventType(title: String, message: String?): String {
        val text = "$title ${message.orEmpty()}".lowercase()
        return when {
            "birthday" in text -> "BIRTHDAY"
            "anniversary" in text -> "ANNIVERSARY"
            else -> "UNKNOWN"
        }
    }
}
