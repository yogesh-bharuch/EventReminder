package com.example.eventreminder.receivers

// =============================================================
// BootReceiver — UUID-only Alarms Restore After Reboot
// =============================================================
//
// Responsibilities:
//  • Restore all ACTIVE reminders after BOOT_COMPLETED / PACKAGE_REPLACED
//  • Detect missed reminders (one-time & recurring)
//  • Immediately fire missed notifications
//  • Compute next occurrence using NextOccurrenceCalculator
//  • Re-schedule alarms using AlarmScheduler.scheduleAllByString()
//  • No DB writes / no repository-side scheduling
//
// Rules:
//  • One-time reminders: fire once if missed, then DO NOT reschedule
//  • Recurring reminders: fire missed event once, then schedule the next
//  • Offsets: always applied the same way as ViewModel
//
// Project Standards:
//  • Named arguments ✓
//  • Section headers ✓
//  • Inline comments ✓
//  • UUID-only reminder IDs ✓
//  • Timber TAG logging ✓
// =============================================================

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.scheduler.AlarmScheduler
import com.example.eventreminder.util.NextOccurrenceCalculator
import com.example.eventreminder.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

private const val TAG = "BootReceiver"

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    // =============================================================
    // Inject Repository (DB-only) + AlarmScheduler (UUID scheduling)
    // =============================================================
    @Inject lateinit var repo: ReminderRepository
    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val isBootEvent =
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
                    intent.action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!isBootEvent) return

        Timber.tag(TAG).i("BOOT_COMPLETED → Restoring all reminders…")

        CoroutineScope(Dispatchers.IO).launch {

            try {
                // =============================================================
                // Load all reminders (enabled + not deleted)
                // =============================================================
                val reminders = repo.getNonDeletedEnabled()
                val now = Instant.now().toEpochMilli()

                reminders.forEach { reminder ->

                    // ---------------------------------------------------------
                    // Offsets → default to 0 if empty
                    // ---------------------------------------------------------
                    val offsets = reminder.reminderOffsets.ifEmpty { listOf(0L) }

                    offsets.forEach { offsetMillis ->

                        val scheduledTrigger = reminder.eventEpochMillis - offsetMillis
                        val isMissed = scheduledTrigger < now

                        // For missed reminders → fire immediately
                        if (isMissed) {
                            fireMissedNotification(
                                context = context,
                                reminderId = reminder.id,
                                title = reminder.title,
                                message = reminder.description.orEmpty(),
                                offsetMillis = offsetMillis
                            )
                        }

                        // ---------------------------------------------------------
                        // Compute next occurrence (ViewModel uses same logic)
                        // ---------------------------------------------------------
                        val nextEventTime =
                            if (reminder.repeatRule.isNullOrEmpty()) {
                                // ONE-TIME:
                                // • missed → do NOT reschedule
                                // • future → schedule normally
                                if (isMissed) null else reminder.eventEpochMillis
                            } else {
                                // RECURRING:
                                // Compute next cycle (may be same day if not missed)
                                NextOccurrenceCalculator.nextOccurrence(
                                    eventEpochMillis = reminder.eventEpochMillis,
                                    zoneIdStr = reminder.timeZone,
                                    repeatRule = reminder.repeatRule
                                )
                            }

                        if (nextEventTime == null) {
                            Timber.tag(TAG).d(
                                "One-time missed & consumed → no reschedule (id=${reminder.id})"
                            )
                            return@forEach
                        }

                        // ---------------------------------------------------------
                        // Reschedule using ViewModel-consistent API
                        // ---------------------------------------------------------
                        scheduler.scheduleAllByString(
                            reminderIdString = reminder.id,
                            title = reminder.title,
                            message = reminder.description.orEmpty(),
                            repeatRule = reminder.repeatRule,
                            nextEventTime = nextEventTime,
                            offsets = listOf(offsetMillis)
                        )

                        Timber.tag(TAG).d(
                            "Rescheduled UUID → id=${reminder.id}, next=$nextEventTime, offset=$offsetMillis"
                        )
                    }
                }

            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "BOOT restore FAILED")
            }
        }
    }

    // =============================================================
    // Fire Missed Notification (One-Time or Recurring)
    // =============================================================
    private fun fireMissedNotification(
        context: Context,
        reminderId: String,
        title: String,
        message: String,
        offsetMillis: Long
    ) {
        val notificationId =
            generateNotificationIdFromString(reminderId, offsetMillis)

        NotificationHelper.showNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            message = message,
            eventType = inferEventType(title, message),
            extras = mapOf(
                ReminderReceiver.EXTRA_REMINDER_ID_STRING to reminderId,
                ReminderReceiver.EXTRA_FROM_NOTIFICATION to true
            )
        )

        Timber.tag(TAG).d(
            "Missed → Fired immediately (id=$reminderId offset=$offsetMillis)"
        )
    }

    // =============================================================
    // Deterministic UUID → Notification ID
    // Matches AlarmScheduler hashing
    // =============================================================
    private fun generateNotificationIdFromString(
        idString: String,
        offsetMillis: Long
    ): Int {
        val raw = idString.hashCode() xor offsetMillis.hashCode()
        return if (raw == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(raw)
    }

    // =============================================================
    // Event Category Detection (for NotificationHelper)
    // =============================================================
    private fun inferEventType(
        title: String,
        message: String?
    ): String {
        val combined = "$title ${message.orEmpty()}".lowercase()

        return when {
            combined.contains("birthday") -> "BIRTHDAY"
            combined.contains("anniversary") -> "ANNIVERSARY"
            else -> "UNKNOWN"
        }
    }
}
