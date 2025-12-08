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

        Timber.tag(TAG).i("BOOT_COMPLETED → restoring UUID reminders...")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminders = repo.getAllOnce()
                val now = Instant.now().toEpochMilli()

                reminders.forEach { reminder ->

                    // -------------------------------
                    // Skip disabled reminders
                    // -------------------------------
                    if (!reminder.enabled) {
                        Timber.tag(TAG).d("Skip disabled UUID id=${reminder.id}")
                        return@forEach
                    }

                    val offsets = reminder.reminderOffsets.ifEmpty { listOf(0L) }

                    offsets.forEach { offsetMillis ->

                        val scheduledTrigger = reminder.eventEpochMillis - offsetMillis
                        val missed = scheduledTrigger < now

                        val notifId = generateNotificationIdFromString(reminder.id, offsetMillis)
                        val eventType = inferEventType(reminder.title, reminder.description)

                        // ----------------------------------------------------
                        // MISSED — one-time reminder
                        // ----------------------------------------------------
                        if (missed && reminder.repeatRule.isNullOrEmpty()) {

                            NotificationHelper.showNotification(
                                context = context,
                                notificationId = notifId,
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
                                "Fired missed ONE-TIME UUID reminder id=${reminder.id} offset=$offsetMillis"
                            )
                            return@forEach
                        }

                        // ----------------------------------------------------
                        // MISSED — recurring reminder
                        // ----------------------------------------------------
                        val nextTrigger = if (missed) {

                            NotificationHelper.showNotification(
                                context = context,
                                notificationId = notifId,
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
                                "Fired missed RECURRING UUID reminder id=${reminder.id} offset=$offsetMillis"
                            )

                            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                                reminder.eventEpochMillis,
                                reminder.timeZone,
                                reminder.repeatRule
                            ) ?: return@forEach

                            nextEvent - offsetMillis
                        } else {
                            scheduledTrigger
                        }

                        // ----------------------------------------------------
                        // RESCHEDULE (UUID)
                        // ----------------------------------------------------
                        scheduler.scheduleExactByString(
                            reminderIdString = reminder.id,
                            eventTriggerMillis = nextTrigger + offsetMillis,
                            offsetMillis = offsetMillis,
                            title = reminder.title,
                            message = reminder.description.orEmpty(),
                            repeatRule = reminder.repeatRule
                        )

                        Timber.tag(TAG).d(
                            "Scheduled (UUID) id=${reminder.id} next=${nextTrigger + offsetMillis} offset=$offsetMillis"
                        )
                    }
                }

            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "BOOT restore failed")
            }
        }
    }

    // ------------------------------------------------------------
    // Deterministic UUID-based Notification ID
    // ------------------------------------------------------------
    private fun generateNotificationIdFromString(idString: String, offsetMillis: Long): Int {
        val raw = idString.hashCode() xor offsetMillis.hashCode()
        return if (raw == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(raw)
    }

    // ------------------------------------------------------------
    // Infer Event Category
    // ------------------------------------------------------------
    private fun inferEventType(title: String, message: String?): String {
        val combined = "$title ${message.orEmpty()}".lowercase()
        return when {
            "birthday" in combined -> "BIRTHDAY"
            "anniversary" in combined -> "ANNIVERSARY"
            else -> "UNKNOWN"
        }
    }
}
