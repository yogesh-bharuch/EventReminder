package com.example.eventreminder.receivers

// =============================================================
// BootReceiver — UUID-only Alarms Restore After Reboot
// Updated to use per-offset lastFiredAt (Option A Extended).
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

    @Inject lateinit var repo: ReminderRepository
    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val isBootEvent =
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
                    intent.action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!isBootEvent) return

        Timber.tag(TAG).i("BOOT_COMPLETED → Restoring all reminders…")

        CoroutineScope(Dispatchers.IO).launch {

            try {
                val reminders = repo.getNonDeletedEnabled()
                val now = Instant.now().toEpochMilli()

                reminders.forEach { reminder ->

                    val offsets = reminder.reminderOffsets.ifEmpty { listOf(0L) }

                    offsets.forEach { offsetMillis ->

                        val scheduledTrigger = reminder.eventEpochMillis - offsetMillis

                        // Lookup persisted lastFiredAt for this reminderId+offset
                        val lastFiredAt = try {
                            repo.getLastFiredAt(reminder.id, offsetMillis)
                        } catch (t: Throwable) {
                            Timber.tag(TAG).e(t, "Failed read lastFiredAt for ${reminder.id} offset=$offsetMillis")
                            null
                        }

                        val hasAlreadyFired = lastFiredAt != null && lastFiredAt >= scheduledTrigger

                        val isMissed = scheduledTrigger < now

                        // If missed AND not already fired → fire immediately
                        if (isMissed && !hasAlreadyFired) {
                            fireMissedNotification(
                                context = context,
                                reminderId = reminder.id,
                                title = reminder.title,
                                message = reminder.description.orEmpty(),
                                offsetMillis = offsetMillis
                            )

                            // Record lastFiredAt so Boot won't double-fire next time
                            try {
                                repo.upsertLastFiredAt(
                                    reminderId = reminder.id,
                                    offsetMillis = offsetMillis,
                                    ts = System.currentTimeMillis()
                                )
                            } catch (t: Throwable) {
                                Timber.tag(TAG).e(t, "Failed to persist lastFiredAt after boot-fire for ${reminder.id} offset=$offsetMillis")
                            }
                        } else {
                            Timber.tag(TAG).d(
                                "No immediate fire → id=${reminder.id} offset=$offsetMillis missed=$isMissed alreadyFired=$hasAlreadyFired"
                            )
                        }

                        // ---------------------------------------------------------
                        // Compute next occurrence
                        // ---------------------------------------------------------
                        val nextEventTime =
                            if (reminder.repeatRule.isNullOrEmpty()) {
                                // ONE-TIME:
                                // • If it was missed and we fired it above → do NOT reschedule
                                // • If not missed → schedule original event
                                if (isMissed && !hasAlreadyFired) {
                                    null
                                } else {
                                    reminder.eventEpochMillis
                                }
                            } else {
                                // RECURRING → compute next valid occurrence
                                NextOccurrenceCalculator.nextOccurrence(
                                    eventEpochMillis = reminder.eventEpochMillis,
                                    zoneIdStr = reminder.timeZone,
                                    repeatRule = reminder.repeatRule
                                )
                            }

                        if (nextEventTime == null) {
                            if (reminder.repeatRule.isNullOrEmpty()) {
                                Timber.tag(TAG).d("One-time missed & consumed → no reschedule (id=${reminder.id} offset=$offsetMillis)")
                            }
                            return@forEach
                        }

                        // ---------------------------------------------------------
                        // Reschedule the next trigger for this offset
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

    private fun fireMissedNotification(
        context: Context,
        reminderId: String,
        title: String,
        message: String,
        offsetMillis: Long
    ) {
        val notificationId = generateNotificationIdFromString(reminderId, offsetMillis)

        NotificationHelper.showNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            message = message,
            eventType = inferEventType(title, message),
            extras = mapOf(
                com.example.eventreminder.receivers.ReminderReceiver.EXTRA_REMINDER_ID_STRING to reminderId,
                com.example.eventreminder.receivers.ReminderReceiver.EXTRA_FROM_NOTIFICATION to true
            )
        )

        Timber.tag(TAG).d("Missed → Fired immediately (id=$reminderId offset=$offsetMillis)")
    }

    private fun generateNotificationIdFromString(idString: String, offsetMillis: Long): Int {
        val raw = idString.hashCode() xor offsetMillis.hashCode()
        return if (raw == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(raw)
    }

    private fun inferEventType(title: String, message: String?): String {
        val combined = "$title ${message.orEmpty()}".lowercase()
        return when {
            combined.contains("birthday") -> "BIRTHDAY"
            combined.contains("anniversary") -> "ANNIVERSARY"
            else -> "UNKNOWN"
        }
    }
}
