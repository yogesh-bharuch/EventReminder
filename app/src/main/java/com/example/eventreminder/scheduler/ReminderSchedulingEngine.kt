package com.example.eventreminder.scheduler

/*// =============================================================
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
// =============================================================*/

import android.content.Context
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.logging.DELETE_TAG
import com.example.eventreminder.util.NextOccurrenceCalculator
import com.example.eventreminder.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReminderSchedulingEngine"

@Singleton
class ReminderSchedulingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ReminderRepository,
    private val alarmScheduler: AlarmScheduler
) {

    /**
     * Called by:
     * - ReminderViewModel (save / update action)
     *
     * Responsibility:
     * - Cancel existing alarms
     * - Clear fire-states if reminder is disabled
     * - Compute next occurrence
     * - Schedule all valid future offsets
     *
     * Returns:
     * - Unit
     */
    suspend fun processSavedReminder(reminder: EventReminder) {
        Timber.tag(TAG).d(
            "processSavedReminder → id=%s [ReminderSchedulingEngine.kt::processSavedReminder]",
            reminder.id
        )

        cancelOffsets(reminder)

        if (!reminder.enabled) {
            Timber.tag(TAG).d(
                "Reminder disabled — clearing fire-states id=%s [ReminderSchedulingEngine.kt::processSavedReminder]",
                reminder.id
            )
            repo.deleteFireStatesForReminder(reminder.id)
            return
        }

        val nextEventEpochMillis = computeNextEvent(reminder)
            ?: return

        scheduleOffsets(
            reminder = reminder,
            occurrenceEpochMillis = nextEventEpochMillis
        )
    }

    /**
     * Called by:
     * - BootReceiver (BOOT_COMPLETED / PACKAGE_REPLACED)
     *
     * Responsibility:
     * - Restore alarms after reboot
     * - Detect missed fires using ReminderFireState
     * - Fire missed notifications immediately
     * - Schedule future alarms only
     *
     * Returns:
     * - Unit
     */
    suspend fun processBootRestore(
        reminder: EventReminder,
        nowEpochMillis: Long
    ) {
        Timber.tag(TAG).d("processBootRestore → id=%s now=%d [ReminderSchedulingEngine.kt::processBootRestore]", reminder.id, nowEpochMillis)

        if (!reminder.enabled) return

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
                Timber.tag(TAG).w("Missed alarm → firing now id=%s offset=%d [ReminderSchedulingEngine.kt::processBootRestore]", reminder.id, offsetMillis)
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

    /**
     * Called by:
     * - ReminderReceiver (after normal alarm fire)
     *
     * Responsibility:
     * - Record fire-state
     * - Handle post-fire lifecycle
     * - Disable one-time reminders
     * - Schedule next repeat occurrence
     *
     * Returns:
     * - Unit
     */
    suspend fun processRepeatTrigger(reminderId: String, offsetMillis: Long) {
        Timber.tag(TAG).d("processRepeatTrigger → id=%s [ReminderSchedulingEngine.kt::processRepeatTrigger]", reminderId)

        val reminder = repo.getReminder(reminderId) ?: return

        if (!reminder.enabled) {
            cancelOffsets(reminder)
            return
        }

        // ✅ NORMAL FIRE → record fire-state for offset 0
        recordFire(
            reminderId = reminder.id,
            offsetMillis = 0L
        )

        // ---------------------------------------------------------
        // ⭐ ONE-TIME REMINDER EXPIRY (DELETE LIFECYCLE)
        // ---------------------------------------------------------
        if (reminder.repeatRule.isNullOrEmpty()) {

            // 🔒 DO NOT expire on pre-offset fires
            if (offsetMillis != 0L) {
                Timber.tag(TAG).d("Pre-offset fired → keeping reminder alive id=%s offset=%d [ReminderSchedulingEngine.kt::processRepeatTrigger]", reminder.id, offsetMillis)
                return
            }

            // ✅ Expire ONLY on main event fire
            Timber.tag(DELETE_TAG).i("One-time reminder expired on main event fire → id=%s [ReminderSchedulingEngine.kt::processRepeatTrigger]", reminder.id)

            repo.updateEnabled(
                id = reminder.id,
                enabled = false,
                isDeleted = false,
                updatedAt = System.currentTimeMillis()
            )

            cancelOffsets(reminder)
            return
        }


        val next = computeNextEvent(reminder) ?: return

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

    /**
     * INTERNAL — shared fire-state write
     *
     * Called by:
     * - fireNotificationNow (boot / missed)
     * - processRepeatTrigger (normal fire)
     *
     * Responsibility:
     * - Persist lastFiredAt for reminder+offset
     *
     * Returns:
     * - Unit
     */
    internal suspend fun recordFire(
        reminderId: String,
        offsetMillis: Long
    ) {
        val ts = System.currentTimeMillis()

        repo.upsertLastFiredAt(
            reminderId = reminderId,
            offsetMillis = offsetMillis,
            ts = ts
        )

        Timber.tag(TAG).d("Fire recorded → id=%s offset=%d ts=%d [ReminderSchedulingEngine.kt::recordFire]", reminderId, offsetMillis, ts)
    }

    /**
     * IMMEDIATE FIRE (BOOT / MISSED)
     */
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

        recordFire(
            reminderId = reminder.id,
            offsetMillis = offsetMillis
        )
    }

    internal fun computeNextEvent(reminder: EventReminder): Long? =
        try {
            NextOccurrenceCalculator.nextOccurrence(
                eventEpochMillis = reminder.eventEpochMillis,
                zoneIdStr = reminder.timeZone,
                repeatRule = reminder.repeatRule
            )
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Next occurrence computation failed id=%s [ReminderSchedulingEngine.kt::computeNextEvent]", reminder.id)
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

    private fun inferEventType(title: String, message: String?): String {
        val text = "$title ${message.orEmpty()}".lowercase()
        return when {
            "birthday" in text -> "BIRTHDAY"
            "anniversary" in text -> "ANNIVERSARY"
            else -> "UNKNOWN"
        }
    }
}
