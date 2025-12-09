package com.example.eventreminder.receivers

// =============================================================
// BootReceiver — UUID-only Alarms Restore After Reboot
// =============================================================
//
// Responsibilities:
//  - Restore all active reminders after BOOT_COMPLETED / PACKAGE_REPLACED
//  - Detect missed reminders (one-time + recurring)
//  - Fire missed notifications immediately
//  - Reschedule next alarms via AlarmScheduler (UUID-only)
//
// Project Standards Followed:
//  - Named arguments ✓
//  - Section headers ✓
//  - Inline comments ✓
//  - UUID-only reminder IDs ✓
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
    // Injected Dependencies
    // =============================================================
    @Inject lateinit var repo: ReminderRepository
    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        // =============================================================
        // BOOT / PACKAGE REPLACED Detection
        // =============================================================
        val isBootEvent =
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
                    intent.action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!isBootEvent) return

        Timber.tag(TAG).i("BOOT_COMPLETED → Restoring all UUID reminders…")

        CoroutineScope(Dispatchers.IO).launch {

            try {
                // =============================================================
                // Load all reminders (enabled + disabled)
                // =============================================================
                val reminders = repo.getAllOnce()
                val now = Instant.now().toEpochMilli()

                reminders.forEach { reminder ->

                    // ---------------------------------------------------------
                    // Skip disabled reminders
                    // ---------------------------------------------------------
                    if (!reminder.enabled) {
                        Timber.tag(TAG).d("Skipping disabled → id=${reminder.id}")
                        return@forEach
                    }

                    // Offsets: if none → use 0
                    val offsets = reminder.reminderOffsets.ifEmpty { listOf(0L) }

                    offsets.forEach { offsetMillis ->

                        val scheduledTrigger = reminder.eventEpochMillis - offsetMillis
                        val isMissed = scheduledTrigger < now

                        // Deterministic notification ID
                        val notificationId =
                            generateNotificationIdFromString(
                                idString = reminder.id,
                                offsetMillis = offsetMillis
                            )

                        val eventType =
                            inferEventType(
                                title = reminder.title,
                                message = reminder.description
                            )

                        // =============================================================
                        // HANDLE MISSED ONE-TIME REMINDER
                        // =============================================================
                        if (isMissed && reminder.repeatRule.isNullOrEmpty()) {

                            NotificationHelper.showNotification(
                                context = context,
                                notificationId = notificationId,
                                title = reminder.title,
                                message = reminder.description.orEmpty(),
                                eventType = eventType,
                                extras = mapOf(
                                    ReminderReceiver.EXTRA_FROM_NOTIFICATION to true,
                                    ReminderReceiver.EXTRA_REMINDER_ID_STRING to reminder.id,
                                    ReminderReceiver.EXTRA_EVENT_TYPE to eventType
                                )
                            )

                            Timber.tag(TAG).d(
                                "Missed ONE-TIME fired → id=${reminder.id} offset=$offsetMillis"
                            )

                            return@forEach
                        }

                        // =============================================================
                        // HANDLE MISSED RECURRING REMINDER
                        // =============================================================
                        val nextTrigger = if (isMissed) {

                            // Fire immediately
                            NotificationHelper.showNotification(
                                context = context,
                                notificationId = notificationId,
                                title = reminder.title,
                                message = reminder.description.orEmpty(),
                                eventType = eventType,
                                extras = mapOf(
                                    ReminderReceiver.EXTRA_FROM_NOTIFICATION to true,
                                    ReminderReceiver.EXTRA_REMINDER_ID_STRING to reminder.id,
                                    ReminderReceiver.EXTRA_EVENT_TYPE to eventType
                                )
                            )

                            Timber.tag(TAG).d(
                                "Missed RECURRING fired → id=${reminder.id} offset=$offsetMillis"
                            )

                            // Compute next valid event time
                            NextOccurrenceCalculator.nextOccurrence(
                                eventEpochMillis = reminder.eventEpochMillis,
                                zoneIdStr = reminder.timeZone,
                                repeatRule = reminder.repeatRule
                            )?.minus(offsetMillis)
                                ?: return@forEach

                        } else {
                            // Not missed → Use original schedule
                            scheduledTrigger
                        }

                        // =============================================================
                        // RESCHEDULE NEXT TRIGGER (UUID ONLY)
                        // =============================================================
                        scheduler.scheduleExactByString(
                            reminderIdString = reminder.id,
                            eventTriggerMillis = nextTrigger + offsetMillis,
                            offsetMillis = offsetMillis,
                            title = reminder.title,
                            message = reminder.description.orEmpty(),
                            repeatRule = reminder.repeatRule
                        )

                        Timber.tag(TAG).d(
                            "Scheduled UUID: id=${reminder.id}, next=${nextTrigger + offsetMillis}, offset=$offsetMillis"
                        )
                    }
                }

            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "BOOT RESTORE FAILED")
            }
        }
    }

    // =============================================================
    // Deterministic UUID → Notification ID
    // =============================================================
    private fun generateNotificationIdFromString(
        idString: String,
        offsetMillis: Long
    ): Int {
        val raw = idString.hashCode() xor offsetMillis.hashCode()
        return if (raw == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(raw)
    }

    // =============================================================
    // Event Category Detection
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
