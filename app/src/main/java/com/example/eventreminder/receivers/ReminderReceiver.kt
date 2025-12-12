package com.example.eventreminder.receivers

// =============================================================
// ReminderReceiver — Clean Engine-Driven Trigger Handler (UUID)
// All scheduling, repeat-handling, and fire-state writes are now
// delegated to ReminderSchedulingEngine.
// =============================================================

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.eventreminder.MainActivity
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.scheduler.ReminderSchedulingEngine
import com.example.eventreminder.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
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
        const val EXTRA_REMINDER_ID_STRING = "reminder_id_string"
    }

    @Inject lateinit var repo: ReminderRepository
    @Inject lateinit var schedulingEngine: ReminderSchedulingEngine

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
        // OPEN_CARD Action
        // ---------------------------------------------------------
        if (intent.action == ACTION_OPEN_CARD) {
            val idString = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)
            if (idString.isNullOrBlank()) {
                Timber.tag(TAG).e("❌ ACTION_OPEN_CARD but UUID missing")
                return
            }

            val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
            val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
            val eventType = inferEventType(title, message)

            Timber.tag(TAG).d("📬 ACTION_OPEN_CARD → Forwarding UUID=$idString")

            val activityIntent = Intent(context, MainActivity::class.java).apply {
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

        val eventType = inferEventType(title, message)

        // Create deterministic notification ID
        val notificationId = generateNotificationIdFromString(idString, offsetMillis)

        // SHOW NOTIFICATION (no fire-state update here anymore)
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
        // Delegate fire-state update + repeat scheduling to ENGINE
        // ---------------------------------------------------------
        CoroutineScope(Dispatchers.IO).launch {
            try {
                schedulingEngine.processRepeatTrigger(
                    reminderId = idString
                )
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Engine repeat-trigger failed for $idString")
            }
        }
    }

    private fun generateNotificationIdFromString(idString: String, offsetMillis: Long): Int {
        val raw = idString.hashCode() xor offsetMillis.hashCode()
        return if (raw == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(raw)
    }

    private fun inferEventType(title: String, message: String): String {
        val text = "$title $message".lowercase()
        return when {
            "birthday" in text -> "BIRTHDAY"
            "anniversary" in text -> "ANNIVERSARY"
            else -> "UNKNOWN"
        }
    }
}
