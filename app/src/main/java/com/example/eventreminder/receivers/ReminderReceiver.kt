package com.example.eventreminder.receivers

import android.app.NotificationManager
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

// =============================================================
// Constants
// =============================================================
private const val TAG = "ReminderReceiver"

/**
 * ReminderReceiver
 *
 * Handles alarm triggers → shows notification + reschedules next event.
 * Enhanced for:
 *  - ACTION_DISMISS from notification action (cancels notification)
 *  - ACTION_OPEN_CARD is handled by PendingIntent → MainActivity
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_REPEAT_RULE = "extra_repeat_rule"
        const val EXTRA_OFFSET_MILLIS = "offsetMillis"

        // Card-generation routing extras
        const val EXTRA_FROM_NOTIFICATION = "from_notification"
        const val EXTRA_EVENT_TYPE = "event_type"  // BIRTHDAY / ANNIVERSARY

        // Notification actions
        const val ACTION_DISMISS = "com.example.eventreminder.ACTION_DISMISS"
        const val ACTION_OPEN_CARD = "com.example.eventreminder.ACTION_OPEN_CARD"

        // Used to pass/cancel notification id
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    @Inject lateinit var repo: ReminderRepository
    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {

        Timber.tag("RR_FLOW").e("ReminderReceiver → onReceive fired, action=${intent.action}")

        // =============================================================
        // Handle notification action: DISMISS
        // =============================================================
        val action = intent.action
        if (action == ACTION_DISMISS) {
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
            Timber.tag(TAG).d("Received ACTION_DISMISS → notificationId=$notificationId")
            if (notificationId != -1) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notificationId)
                Timber.tag(TAG).d("Notification cancelled → id=$notificationId")
            }
            return
        }

        // =============================================================
        // Otherwise, this BroadcastReceiver was triggered by an Alarm
        // =============================================================
        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        if (id == -1L) return

        val title   = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val repeatRule   = intent.getStringExtra(EXTRA_REPEAT_RULE)
        val offsetMillis = intent.getLongExtra(EXTRA_OFFSET_MILLIS, 0L)

        // Derive event type once (used for emoji + card routing)
        val eventType = inferEventType(title, message)

        // =============================================================
        // Build deterministic notification id from reminder id + offset
        // so it can be reliably cancelled later.
        // =============================================================
        val notificationId = generateNotificationId(id, offsetMillis)

        // =============================================================
        // Show notification → includes deep-link extras + actions
        // (NotificationHelper internally picks sound via RingtoneResolver)
        // =============================================================
        Timber.tag("RR_FLOW").e("Calling NotificationHelper for id=$id title=$title message=$message")
        NotificationHelper.showNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            message = message,
            eventType = eventType,
            // pass routing extras for CardScreen
            extras = mapOf(
                EXTRA_FROM_NOTIFICATION to true,
                "reminder_id" to id,
                EXTRA_EVENT_TYPE to eventType
            )
        )

        // =============================================================
        // Recurring Scheduling
        // =============================================================
        CoroutineScope(Dispatchers.IO).launch {
            val r = repo.getReminder(id) ?: return@launch

            if (repeatRule.isNullOrEmpty()) return@launch   // no repeat → done

            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                r.eventEpochMillis,
                r.timeZone,
                r.repeatRule
            ) ?: return@launch

            val offsets = r.reminderOffsets.ifEmpty { listOf(0L) }

            scheduler.scheduleAll(
                reminderId = id,
                title = r.title,
                message = r.description ?: "",
                repeatRule = r.repeatRule,
                nextEventTime = nextEvent,
                offsets = offsets
            )

            Timber.tag(TAG).d(
                "Rescheduled recurring id=$id → next=${Instant.ofEpochMilli(nextEvent)}"
            )
        }
    }

    // =============================================================
    // Helper — deterministic notification id
    // =============================================================
    private fun generateNotificationId(reminderId: Long, offsetMillis: Long): Int {
        // Combine reminderId and offsetMillis into a single stable int.
        val low = reminderId.toInt()
        val high = (reminderId ushr 32).toInt()
        val offsetHash = (offsetMillis xor (offsetMillis ushr 32)).toInt()
        return (low xor high xor offsetHash).absoluteSafe()
    }

    // Safe absolute conversion (ensure positive and not Int.MIN_VALUE)
    private fun Int.absoluteSafe(): Int {
        return if (this == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(this)
    }

    // =============================================================
    // Helper to infer card type from title/message
    // =============================================================
    private fun inferEventType(title: String, message: String): String {
        val raw = "$title $message".lowercase()

        return when {
            raw.contains("birthday")     -> "BIRTHDAY"
            raw.contains("anniversary")  -> "ANNIVERSARY"
            else                         -> "UNKNOWN"
        }
    }
}
