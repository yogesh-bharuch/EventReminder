package com.example.eventreminder.receivers

// =============================================================
// Imports
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
import com.example.eventreminder.receivers.ReminderReceiver
import com.example.eventreminder.receivers.ReminderReceiver.Companion.EXTRA_EVENT_TYPE
import com.example.eventreminder.receivers.ReminderReceiver.Companion.EXTRA_FROM_NOTIFICATION
import com.example.eventreminder.receivers.ReminderReceiver.Companion.EXTRA_REMINDER_ID

// =============================================================
// Constants
// =============================================================
private const val TAG = "BootReceiver"

/**
 * BootReceiver
 *
 * Restores and (if needed) fires reminders after:
 *  - Device reboot (ACTION_BOOT_COMPLETED)
 *  - App replace/update (ACTION_MY_PACKAGE_REPLACED)
 *
 * Behavior:
 *  - Fires missed one-time reminders immediately.
 *  - Fires missed recurring reminders immediately then schedules next occurrence.
 *  - Posts notifications using NotificationHelper with the same extras & notificationId
 *    as ReminderReceiver so navigation & dismissal work uniformly.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    // =============================================================
    // Injected dependencies
    // =============================================================
    @Inject
    lateinit var repo: ReminderRepository

    @Inject
    lateinit var scheduler: AlarmScheduler

    // =============================================================
    // Entry point
    // =============================================================
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isBootEvent = action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!isBootEvent) return

        Timber.tag(TAG).i("BOOT/MY_PACKAGE_REPLACED received — restoring reminders")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminders = repo.getAllOnce()
                val now = Instant.now().toEpochMilli()

                reminders.forEach { reminder ->

                    if (!reminder.enabled) {
                        Timber.tag(TAG).d("Skipping disabled reminder id=${reminder.id}")
                        return@forEach
                    }

                    val offsets = reminder.reminderOffsets.ifEmpty { listOf(0L) }

                    offsets.forEach { offsetMillis ->

                        // =============================================================
                        // Compute when this offset should have triggered
                        // =============================================================
                        val scheduledTrigger = reminder.eventEpochMillis - offsetMillis
                        val missed = scheduledTrigger < now

                        // Deterministic notification id (must match ReminderReceiver)
                        val notificationId = generateNotificationId(reminder.id, offsetMillis)

                        // Event type inference for emoji/styling
                        val eventType = inferEventType(reminder.title, reminder.description)

                        // =============================================================
                        // MISSED — One-time reminder: fire immediately (no reschedule)
                        // =============================================================
                        if (missed && reminder.repeatRule.isNullOrEmpty()) {
                            NotificationHelper.showNotification(
                                context = context,
                                notificationId = notificationId,
                                title = reminder.title,
                                message = reminder.description.orEmpty(),
                                eventType = eventType,
                                extras = mapOf(
                                    EXTRA_FROM_NOTIFICATION to true,
                                    EXTRA_REMINDER_ID to reminder.id,
                                    EXTRA_EVENT_TYPE to eventType
                                )
                            )

                            Timber.tag(TAG).d("Fired missed one-time reminder id=${reminder.id} offset=$offsetMillis")
                            return@forEach
                        }

                        // =============================================================
                        // MISSED — Recurring reminder: fire missed instance, compute next
                        // =============================================================
                        val nextTrigger = if (missed) {

                            // Fire the missed occurrence now
                            NotificationHelper.showNotification(
                                context = context,
                                notificationId = notificationId,
                                title = reminder.title,
                                message = reminder.description.orEmpty(),
                                eventType = eventType,
                                extras = mapOf(
                                    EXTRA_FROM_NOTIFICATION to true,
                                    EXTRA_REMINDER_ID to reminder.id,
                                    EXTRA_EVENT_TYPE to eventType
                                )
                            )

                            Timber.tag(TAG).d("Fired missed recurring reminder id=${reminder.id} offset=$offsetMillis")

                            // Compute next occurrence based on recurrence rule
                            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                                reminder.eventEpochMillis,
                                reminder.timeZone,
                                reminder.repeatRule
                            ) ?: return@forEach

                            // Return base nextEvent adjusted for this offset
                            nextEvent - offsetMillis

                        } else {
                            // Not missed — schedule at originally computed time
                            scheduledTrigger
                        }

                        // =============================================================
                        // RESCHEDULE this offset for the next occurrence
                        // Pass eventTriggerMillis as base time (scheduleExact will subtract offset)
                        // =============================================================
                        scheduler.scheduleExact(
                            reminderId = reminder.id,
                            eventTriggerMillis = nextTrigger + offsetMillis,
                            offsetMillis = offsetMillis,
                            title = reminder.title,
                            message = reminder.description.orEmpty(),
                            repeatRule = reminder.repeatRule
                        )

                        Timber.tag(TAG).d("Scheduled reminder id=${reminder.id} offset=$offsetMillis next=$nextTrigger")
                    }
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Failed to restore reminders on boot")
            }
        }
    }

    // =============================================================
    // Deterministic notification id generator — keep identical to ReminderReceiver
    // =============================================================
    private fun generateNotificationId(reminderId: Long, offsetMillis: Long): Int {
        val low = reminderId.toInt()
        val high = (reminderId ushr 32).toInt()
        val off = (offsetMillis xor (offsetMillis ushr 32)).toInt()
        val raw = low xor high xor off
        return if (raw == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(raw)
    }

    // =============================================================
    // Infer event type used by NotificationHelper for emoji/styling
    // =============================================================
    private fun inferEventType(title: String, message: String?): String {
        val raw = "$title ${message.orEmpty()}".lowercase()
        return when {
            raw.contains("birthday") -> "BIRTHDAY"
            raw.contains("anniversary") -> "ANNIVERSARY"
            else -> "UNKNOWN"
        }
    }
}
