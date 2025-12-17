package com.example.eventreminder.scheduler

// =============================================================
// ReminderSchedulingEngine — Master Scheduling Engine (UUID-only)
// =============================================================
//
// Single source-of-truth for:
//  • Scheduling alarms
//  • Handling missed fires (boot restore)
//  • Repeat rescheduling
//  • Per-offset lastFiredAt bookkeeping
//  • Cancelling alarms
//  • ❗ Lifecycle transition of one-time reminders
//
// IMPORTANT RULE (AGREED):
//  ------------------------------------------------------------
//  If a reminder has:
//   • repeatRule == null (one-time)
//   • AND no next occurrence exists
//
//  → it MUST be disabled (enabled = false)
//  → UI will then move it to "Past 30 Days"
//  → GC will later delete it
//
// =============================================================

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.util.NextOccurrenceCalculator
import com.example.eventreminder.util.NotificationHelper
import java.time.Instant
import com.example.eventreminder.logging.DELETE_TAG

private const val TAG = "ReminderSchedulingEngine"

@Singleton
class ReminderSchedulingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ReminderRepository,
    private val alarmScheduler: AlarmScheduler
) {

    // =============================================================
    // SAVE / UPDATE ENTRY POINT
    // =============================================================
    suspend fun processSavedReminder(reminder: EventReminder) {
        Timber.tag(TAG).d("processSavedReminder: %s", reminder.id)

        cancelOffsets(reminder)

        if (!reminder.enabled) {
            Timber.tag(TAG).d("Reminder %s disabled — clearing fire-states and returning", reminder.id)
            repo.deleteFireStatesForReminder(reminder.id)
            return
        }

        val nextEventEpochMillis = computeNextEvent(reminder)

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
    // BOOT RESTORE
    // =============================================================
    suspend fun processBootRestore(
        reminder: EventReminder,
        nowEpochMillis: Long
    ) {
        Timber.tag(TAG).d("processBootRestore: %s at %d", reminder.id, nowEpochMillis)

        if (!reminder.enabled) {
            Timber.tag(TAG).d("Reminder %s disabled on boot — skipping", reminder.id)
            return
        }

        val offsets = reminder.reminderOffsets.ifEmpty { listOf(0L) }

        val nextEventEpochMillis =
            if (reminder.repeatRule.isNullOrEmpty()) {
                reminder.eventEpochMillis
            } else {
                NextOccurrenceCalculator.nextOccurrence(
                    eventEpochMillis = reminder.eventEpochMillis,
                    zoneIdStr = reminder.timeZone,
                    repeatRule = reminder.repeatRule
                )
            }

        for (offsetMillis in offsets) {
            val scheduledTrigger =
                (nextEventEpochMillis ?: reminder.eventEpochMillis) - offsetMillis

            val lastFiredAt = repo.getLastFiredAt(reminder.id, offsetMillis)
            val hasAlreadyFired =
                lastFiredAt != null && lastFiredAt >= scheduledTrigger
            val isMissed = scheduledTrigger < nowEpochMillis

            if (isMissed && !hasAlreadyFired) {
                Timber.tag(TAG).w("Boot: Missed alarm → firing now id=%s offset=%d", reminder.id, offsetMillis)

                fireNotificationNow(reminder, offsetMillis)
            } else if (scheduledTrigger > nowEpochMillis) {
                alarmScheduler.scheduleExactByString(
                    reminderIdString = reminder.id,
                    eventTriggerMillis =
                        nextEventEpochMillis ?: reminder.eventEpochMillis,
                    offsetMillis = offsetMillis,
                    title = reminder.title,
                    message = reminder.description.orEmpty(),
                    repeatRule = reminder.repeatRule
                )
            }
        }
    }

    // =============================================================
    // REPEAT TRIGGER (AFTER NOTIFICATION FIRE)
    // =============================================================
    suspend fun processRepeatTrigger(reminderId: String) {

        Timber.tag(TAG).d("processRepeatTrigger: %s", reminderId)

        val reminder = repo.getReminder(reminderId) ?: run {
            Timber.tag(TAG).w("processRepeatTrigger: reminder not found %s", reminderId)
            return
        }

        if (!reminder.enabled) {
            cancelOffsets(reminder)
            return
        }

        // ---------------------------------------------------------
        // ⭐ ONE-TIME REMINDER EXPIRY (DELETE LIFECYCLE)
        // ---------------------------------------------------------
        if (reminder.repeatRule.isNullOrEmpty()) {

            Timber.tag(DELETE_TAG).i("One-time reminder expired on fire → id=%s", reminder.id)

            repo.updateEnabled(
                id = reminder.id,
                enabled = false,
                updatedAt = System.currentTimeMillis()
            )

            cancelOffsets(reminder)
            return
        }

        val next = computeNextEvent(reminder)

        if (next == null) {
            cancelOffsets(reminder)
            return
        }

        scheduleOffsets(
            reminder = reminder,
            occurrenceEpochMillis = next
        )
    }

    // =============================================================
    // DELETE (HARD / USER / GC)
    // =============================================================
    suspend fun processDelete(reminder: EventReminder) {
        Timber.tag(DELETE_TAG).w("processDelete → id=%s", reminder.id)
        cancelOffsets(reminder)
        repo.deleteFireStatesForReminder(reminder.id)
    }

    // =============================================================
    // INTERNAL HELPERS
    // =============================================================
    internal fun computeNextEvent(reminder: EventReminder): Long? =
        try {
            NextOccurrenceCalculator.nextOccurrence(
                eventEpochMillis = reminder.eventEpochMillis,
                zoneIdStr = reminder.timeZone,
                repeatRule = reminder.repeatRule
            )
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to compute next event for %s", reminder.id)
            null
        }

    internal fun cancelOffsets(reminder: EventReminder) {
        val offsets = reminder.reminderOffsets.ifEmpty { listOf(0L) }
        alarmScheduler.cancelAllByString(
            reminderIdString = reminder.id,
            offsets = offsets
        )
    }

    internal fun scheduleOffsets(
        reminder: EventReminder,
        occurrenceEpochMillis: Long
    ) {
        val now = Instant.now().toEpochMilli()
        val offsets = reminder.reminderOffsets.ifEmpty { listOf(0L) }

        val futureOffsets = offsets.filter {
            (occurrenceEpochMillis - it) > now
        }

        if (futureOffsets.isEmpty()) return

        alarmScheduler.scheduleAllByString(
            reminderIdString = reminder.id,
            title = reminder.title,
            message = reminder.description.orEmpty(),
            repeatRule = reminder.repeatRule,
            nextEventTime = occurrenceEpochMillis,
            offsets = futureOffsets
        )
    }

    // =============================================================
    // IMMEDIATE FIRE (BOOT / MISSED)
    // =============================================================
    internal suspend fun fireNotificationNow(
        reminder: EventReminder,
        offsetMillis: Long
    ) {
        val raw = reminder.id.hashCode() xor offsetMillis.hashCode()
        val notificationId =
            if (raw == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(raw)

        NotificationHelper.showNotification(
            context = context,
            notificationId = notificationId,
            title = reminder.title,
            message = reminder.description.orEmpty(),
            eventType = inferEventType(reminder.title, reminder.description),
            extras = mapOf(
                com.example.eventreminder.receivers.ReminderReceiver
                    .EXTRA_REMINDER_ID_STRING to reminder.id
            )
        )

        repo.upsertLastFiredAt(
            reminderId = reminder.id,
            offsetMillis = offsetMillis,
            ts = System.currentTimeMillis()
        )
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
