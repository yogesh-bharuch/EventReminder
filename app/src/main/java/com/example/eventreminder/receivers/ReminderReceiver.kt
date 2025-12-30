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

        Timber.tag(TAG).i("Receiver fired → action=${intent.action} [ReminderReceiver.kt::onReceive]")

        // ---------------------------------------------------------
        // DISMISS Action (UI → DB only)
        // ---------------------------------------------------------


        if (intent.action == ACTION_DISMISS) {

            val notificationId =
                intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

            val reminderId =
                intent.getStringExtra(EXTRA_REMINDER_ID_STRING)

            val offsetMillis =
                intent.getLongExtra(EXTRA_OFFSET_MILLIS, 0L)

            Timber.tag("DISMISS").e(
                "DISMISS_RECEIVED → notifId=%d uuid=%s offset=%d [ReminderReceiver.kt::onReceive]",
                notificationId,
                reminderId,
                offsetMillis
            )

            // Cancel notification immediately
            if (notificationId != -1) {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.cancel(notificationId)
                Timber.tag(TAG).d(
                    "Notification dismissed → id=$notificationId [ReminderReceiver.kt::onReceive]"
                )
            }

            // Persist dismiss event (async, no UI blocking)
            if (!reminderId.isNullOrBlank()) {
                CoroutineScope(Dispatchers.IO).launch {

                    try {
                        repo.recordDismissed(
                            reminderId = reminderId,
                            offsetMillis = offsetMillis
                        )
                    } catch (t: Throwable) {
                        Timber.tag(TAG).e(
                            t,
                            "Failed to record dismiss → id=$reminderId off=$offsetMillis [ReminderReceiver.kt::onReceive]"
                        )
                    }
                }
            } else {
                Timber.tag(TAG).w(
                    "Dismiss action without reminderId [ReminderReceiver.kt::onReceive]"
                )
            }

            return
        }

        // ---------------------------------------------------------
        // OPEN_CARD Action
        // ---------------------------------------------------------
        if (intent.action == ACTION_OPEN_CARD) {
            val idString = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)
            if (idString.isNullOrBlank()) {
                Timber.tag(TAG).e("❌ ACTION_OPEN_CARD but UUID missing [ReminderReceiver.kt::onReceive]")
                return
            }

            val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
            val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
            val eventType = inferEventType(title, message)

            Timber.tag(TAG).d(
                "📬 ACTION_OPEN_CARD → Forwarding UUID=$idString [ReminderReceiver.kt::onReceive]"
            )

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
            Timber.tag(TAG).e("❌ Alarm received but missing UUID [ReminderReceiver.kt::onReceive]")
            return
        }

        Timber.tag(TAG).d("🔔 Alarm trigger → UUID=$idString [ReminderReceiver.kt::onReceive]")

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val repeatRule = intent.getStringExtra(EXTRA_REPEAT_RULE)
        val offsetMillis = intent.getLongExtra(EXTRA_OFFSET_MILLIS, 0L)

        val eventType = inferEventType(title, message)

        val notificationId =
            generateNotificationIdFromString(idString, offsetMillis)

        NotificationHelper.showNotification(
            context = context,
            notificationId = notificationId,
            title = title,
            message = message,
            eventType = eventType,
            extras = mapOf(
                EXTRA_FROM_NOTIFICATION to true,
                EXTRA_REMINDER_ID_STRING to idString,
                EXTRA_EVENT_TYPE to eventType,
                EXTRA_OFFSET_MILLIS to offsetMillis
            )
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                schedulingEngine.processRepeatTrigger(
                    reminderId = idString,
                    offsetMillis = offsetMillis
                )
            } catch (t: Throwable) {
                Timber.tag(TAG).e(
                    t,
                    "Engine repeat-trigger failed for $idString [ReminderReceiver.kt::onReceive]"
                )
            }
        }
    }

    private fun generateNotificationIdFromString(
        idString: String,
        offsetMillis: Long
    ): Int {
        val raw = idString.hashCode() xor offsetMillis.hashCode()
        return if (raw == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(raw)
    }

    private fun inferEventType(title: String, message: String): String {
        val titleText = title.lowercase()
        val messageText = message.lowercase()

        return when {
            "birthday" in titleText -> "BIRTHDAY"
            "anniversary" in titleText -> "ANNIVERSARY"
            "medicine" in titleText -> "MEDICINE"
            "workout" in titleText -> "WORKOUT"
            "meeting" in titleText -> "MEETING"
            "pill" in messageText || "tablet" in messageText -> "MEDICINE"
            "exercise" in messageText || "gym" in messageText -> "WORKOUT"
            "meet" in messageText -> "MEETING"
            else -> "GENERAL"
        }
    }
}
