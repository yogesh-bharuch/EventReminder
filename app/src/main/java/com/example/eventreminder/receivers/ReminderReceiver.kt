package com.example.eventreminder.receivers

// =============================================================
// UUID-ONLY ReminderReceiver (Final, Clean, Error-Free)
// =============================================================
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

private const val TAG = "ReminderReceiver"

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    companion object {

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_REPEAT_RULE = "extra_repeat_rule"
        const val EXTRA_OFFSET_MILLIS = "offsetMillis"

        const val EXTRA_FROM_NOTIFICATION = "from_notification"
        const val EXTRA_EVENT_TYPE = "event_type"

        const val ACTION_DISMISS = "com.example.eventreminder.ACTION_DISMISS"
        const val ACTION_OPEN_CARD = "com.example.eventreminder.ACTION_OPEN_CARD"

        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        // UUID (String) → Only valid ID in new schema
        const val EXTRA_REMINDER_ID_STRING = "extra_reminder_id_string"
    }

    @Inject lateinit var repo: ReminderRepository
    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {

        Timber.tag(TAG).i("ReminderReceiver fired: action=${intent.action}")

        // ---------------------------------------------------------
        // Notification dismissal
        // ---------------------------------------------------------
        if (intent.action == ACTION_DISMISS) {
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
            if (notificationId != -1) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(notificationId)
                Timber.tag(TAG).d("Dismissed notification id=$notificationId")
            }
            return
        }

        // ---------------------------------------------------------
        // UUID ID channel — the only valid path now
        // ---------------------------------------------------------
        val idString = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)

        if (idString.isNullOrBlank()) {
            Timber.tag(TAG).e("No UUID idString found in broadcast → ignoring")
            return
        }

        Timber.tag(TAG).d("UUID reminder triggered → idString=$idString")

        val title   = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val repeatRule = intent.getStringExtra(EXTRA_REPEAT_RULE)
        val offsetMillis = intent.getLongExtra(EXTRA_OFFSET_MILLIS, 0L)

        val eventType = inferEventType(title, message)

        // Build deterministic notification id (UUID only)
        val notificationId = generateNotificationIdFromString(idString, offsetMillis)

        // ---------------------------------------------------------
        // Show notification immediately
        // ---------------------------------------------------------
        NotificationHelper.showNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            message = message,
            eventType = eventType,
            extras = mapOf(
                EXTRA_FROM_NOTIFICATION to true,
                EXTRA_REMINDER_ID_STRING to idString,
                EXTRA_EVENT_TYPE to eventType
            )
        )

        // ---------------------------------------------------------
        // Reschedule if repeating
        // ---------------------------------------------------------
        CoroutineScope(Dispatchers.IO).launch {

            val reminder = repo.getReminder(idString)   // ✔ correct UUID lookup
                ?: return@launch

            if (reminder.repeatRule.isNullOrEmpty()) {
                Timber.tag(TAG).d("UUID reminder $idString is one-time → no reschedule")
                return@launch
            }

            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                reminder.eventEpochMillis,
                reminder.timeZone,
                reminder.repeatRule
            ) ?: return@launch

            val offsets = reminder.reminderOffsets.ifEmpty { listOf(0L) }

            scheduler.scheduleAllByString(
                reminderIdString = idString,
                title = reminder.title,
                message = reminder.description ?: "",
                repeatRule = reminder.repeatRule,
                nextEventTime = nextEvent,
                offsets = offsets
            )

            Timber.tag(TAG).d(
                "Rescheduled UUID reminder idString=$idString next=${Instant.ofEpochMilli(nextEvent)}"
            )
        }
    }

    // =============================================================
    // Deterministic UUID → Notification ID
    // =============================================================
    private fun generateNotificationIdFromString(idString: String, offsetMillis: Long): Int {
        val raw = idString.hashCode() xor offsetMillis.hashCode()
        return if (raw == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(raw)
    }

    // =============================================================
    // Utility
    // =============================================================
    private fun inferEventType(title: String, message: String): String {
        val lower = "$title $message".lowercase()
        return when {
            "birthday" in lower -> "BIRTHDAY"
            "anniversary" in lower -> "ANNIVERSARY"
            else -> "UNKNOWN"
        }
    }
}
