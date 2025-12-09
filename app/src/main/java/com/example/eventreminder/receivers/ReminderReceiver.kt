package com.example.eventreminder.receivers

// =============================================================
// ReminderReceiver — UUID-Only Reminder Trigger Handler
// -------------------------------------------------------------
// Responsibilities:
//  • Receive alarm broadcasts
//  • Show notification immediately
//  • Handle DISMISS action
//  • Handle OPEN_CARD action (fixed to send correct UUID key)
//  • Reschedule repeating reminders
//
// Project Standards:
//  • Named arguments
//  • Section headers
//  • Inline comments
//  • UUID-only ID schema
// =============================================================

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.eventreminder.MainActivity
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

        // Payload
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_REPEAT_RULE = "extra_repeat_rule"
        const val EXTRA_OFFSET_MILLIS = "offsetMillis"

        // Metadata
        const val EXTRA_FROM_NOTIFICATION = "from_notification"
        const val EXTRA_EVENT_TYPE = "event_type"

        // Actions
        const val ACTION_DISMISS = "com.example.eventreminder.ACTION_DISMISS"
        const val ACTION_OPEN_CARD = "com.example.eventreminder.ACTION_OPEN_CARD"

        // Notification
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        // UUID key — MUST MATCH MainActivity
        const val EXTRA_REMINDER_ID_STRING = "reminder_id_string"
    }

    // =============================================================
    // Injected Dependencies
    // =============================================================
    @Inject lateinit var repo: ReminderRepository
    @Inject lateinit var scheduler: AlarmScheduler

    // =============================================================
    // Entry Point
    // =============================================================
    override fun onReceive(context: Context, intent: Intent) {

        Timber.tag(TAG).i("Receiver fired → action=${intent.action}")

        // ---------------------------------------------------------
        // DISMISS Action
        // ---------------------------------------------------------
        if (intent.action == ACTION_DISMISS) {
            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
            if (notificationId != -1) {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.cancel(notificationId)
                Timber.tag(TAG).d("Dismissed → id=$notificationId")
            }
            return
        }

        // ---------------------------------------------------------
        // OPEN_CARD Action (clicking notification button)
        // ---------------------------------------------------------
        if (intent.action == ACTION_OPEN_CARD) {

            val idString = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)

            if (idString.isNullOrBlank()) {
                Timber.tag(TAG).e("❌ ACTION_OPEN_CARD but UUID missing")
                return
            }

            val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
            val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
            val eventType = inferEventType(title = title, message = message)

            Timber.tag(TAG).d("📬 ACTION_OPEN_CARD → Forwarding UUID=$idString")

            val activityIntent = Intent(
                context,
                MainActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

                putExtra(EXTRA_FROM_NOTIFICATION, true)
                putExtra(EXTRA_REMINDER_ID_STRING, idString)
                putExtra(EXTRA_EVENT_TYPE, eventType)
            }

            context.startActivity(activityIntent)
            return
        }

        // ---------------------------------------------------------
        // NORMAL ALARM TRIGGER
        // ---------------------------------------------------------
        val idString = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)

        if (idString.isNullOrBlank()) {
            Timber.tag(TAG).e("❌ Alarm received but missing UUID")
            return
        }

        Timber.tag(TAG).d("🔔 Alarm trigger → UUID=$idString")

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val repeatRule = intent.getStringExtra(EXTRA_REPEAT_RULE)
        val offsetMillis = intent.getLongExtra(EXTRA_OFFSET_MILLIS, 0L)

        val eventType = inferEventType(title = title, message = message)

        // Deterministic Notification ID
        val notificationId = generateNotificationIdFromString(
            idString = idString,
            offsetMillis = offsetMillis
        )

        // Show notification
        NotificationHelper.showNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            message = message,
            eventType = eventType,
            extras = mapOf(
                EXTRA_FROM_NOTIFICATION to true,
                EXTRA_REMINDER_ID_STRING to idString,   // MUST MATCH MainActivity
                EXTRA_EVENT_TYPE to eventType
            )
        )

        // ---------------------------------------------------------
        // Reschedule if repeating
        // ---------------------------------------------------------
        CoroutineScope(Dispatchers.IO).launch {
            val reminder = repo.getReminder(id = idString) ?: return@launch

            if (reminder.repeatRule.isNullOrEmpty()) {
                Timber.tag(TAG).d("No repeat → done")
                return@launch
            }

            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                eventEpochMillis = reminder.eventEpochMillis,
                zoneIdStr = reminder.timeZone,
                repeatRule = reminder.repeatRule
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

            Timber.tag(TAG).d("Rescheduled → next=${Instant.ofEpochMilli(nextEvent)}")
        }
    }

    // =============================================================
    // Deterministic Notification ID
    // =============================================================
    private fun generateNotificationIdFromString(
        idString: String,
        offsetMillis: Long
    ): Int {
        val raw = idString.hashCode() xor offsetMillis.hashCode()
        return if (raw == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(raw)
    }

    // =============================================================
    // Event Type Resolver
    // =============================================================
    private fun inferEventType(title: String, message: String): String {
        val text = "$title $message".lowercase()
        return when {
            "birthday" in text -> "BIRTHDAY"
            "anniversary" in text -> "ANNIVERSARY"
            else -> "UNKNOWN"
        }
    }
}
