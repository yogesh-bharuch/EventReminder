package com.example.eventreminder.receivers

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
import com.example.eventreminder.logging.DISMISS_TAG
import com.example.eventreminder.logging.SHARE_PDF_TAG
import com.example.eventreminder.data.delivery.PdfDeliveryLedger   // ✅ Step 4.3
import java.time.LocalDate
import java.time.ZoneId

private const val TAG = "ReminderReceiver"

/**
 * ReminderReceiver
 *
 * A BroadcastReceiver that handles different reminder-related actions:
 * - Dismissing notifications (both reminder and PDF-only notifications)
 * - Opening reminder cards in the MainActivity
 * - Handling normal alarm triggers to show notifications and reschedule repeats
 *
 * This class integrates with:
 * - ReminderRepository (to persist dismiss events)
 * - ReminderSchedulingEngine (to handle repeat reminders)
 * - PdfDeliveryLedger (to mark PDF deliveries as completed)
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        // Keys for extras passed via Intents
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

    // Dependencies injected via Hilt
    @Inject lateinit var repo: ReminderRepository
    @Inject lateinit var schedulingEngine: ReminderSchedulingEngine
    // Step 4.3 — Notification Delivery Ledger
    @Inject lateinit var pdfDeliveryLedger: PdfDeliveryLedger   // ✅ ledger

    /**
     * Entry point for BroadcastReceiver.
     * Handles incoming Intents based on their action type:
     * - ACTION_DISMISS → dismisses notifications
     * - ACTION_OPEN_CARD → opens MainActivity with reminder details
     * - Default (alarm trigger) → shows notification and processes repeat scheduling
     */
    override fun onReceive(context: Context, intent: Intent) {

        Timber.tag(TAG).i("Receiver fired → action=${intent.action} [ReminderReceiver.kt::onReceive]")

        // ---------------------------------------------------------
        // Case 1: DISMISS Action
        // ---------------------------------------------------------
        if (intent.action == ACTION_DISMISS) {

            val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
            val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)
            val offsetMillis = intent.getLongExtra(EXTRA_OFFSET_MILLIS, 0L)

            // PDF-ONLY DISMISS (Next7DaysPdfWorker)
            if (reminderId.isNullOrBlank()) {

                Timber.tag(SHARE_PDF_TAG).i("PDF_DISMISS_RECEIVED → notifId=$notificationId [ReminderReceiver.kt::onReceive]")

                // Cancel the notification
                if (notificationId != -1) {
                    val nm = context.getSystemService(NotificationManager::class.java)
                    nm.cancel(notificationId)

                    Timber.tag(SHARE_PDF_TAG).d("PDF notification dismissed → id=$notificationId [ReminderReceiver.kt::onReceive]")
                }

                // 🧾 Step 4.3 — Update (DataStore) ledger to mark PDF as delivered
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val today = LocalDate.now(ZoneId.systemDefault())
                        pdfDeliveryLedger.markNext7DaysDelivered(today)

                        Timber.tag(SHARE_PDF_TAG).i("PDF_LEDGER_UPDATED delivered_date=$today [ReminderReceiver.kt::onReceive]")
                    } catch (t: Throwable) {
                        Timber.tag(SHARE_PDF_TAG).e(t, "PDF_LEDGER_WRITE_FAILED [ReminderReceiver.kt::onReceive]")
                    }
                }

                // ⛔ Stop further processing (no repo, no scheduling, no reschedule)
                return
            }

            // ---- Normal reminder dismiss ----
            Timber.tag(DISMISS_TAG).i("DISMISS_RECEIVED → notifId=%d uuid=%s offset=%d [ReminderReceiver.kt::onReceive]", notificationId, reminderId, offsetMillis)

            // Cancel notification immediately
            if (notificationId != -1) {
                val nm = context.getSystemService(NotificationManager::class.java)
                nm.cancel(notificationId)

                Timber.tag(DISMISS_TAG).d("Notification dismissed → id=$notificationId [ReminderReceiver.kt::onReceive]")
            }

            // Updates reminder_fire_state table for dismissedat filed  dismiss event (async, no UI blocking)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repo.recordDismissed(
                        reminderId = reminderId,
                        offsetMillis = offsetMillis
                    )
                } catch (t: Throwable) {
                    Timber.tag(TAG).e(t, "Failed to record dismiss → id=$reminderId off=$offsetMillis [ReminderReceiver.kt::onReceive]")
                }
            }

            return
        }

        // ---------------------------------------------------------
        // Case 2: OPEN_CARD Action
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

            Timber.tag(TAG).d("📬 ACTION_OPEN_CARD → Forwarding UUID=$idString [ReminderReceiver.kt::onReceive]")

            // Launch MainActivity with reminder details
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK

                putExtra(EXTRA_FROM_NOTIFICATION, true)
                putExtra(EXTRA_REMINDER_ID_STRING, idString)
                putExtra(EXTRA_EVENT_TYPE, eventType)
            }

            context.startActivity(activityIntent)
            return
        }

        // ---------------------------------------------------------
        // Case 3: Normal Alarm Trigger
        // ---------------------------------------------------------
        val idString = intent.getStringExtra(EXTRA_REMINDER_ID_STRING)
        if (idString.isNullOrBlank()) {
            Timber.tag(TAG).e("❌ Alarm received but missing UUID [ReminderReceiver.kt::onReceive]")
            return
        }

        Timber.tag(TAG).d("🔔 Alarm trigger → UUID=$idString [ReminderReceiver.kt::onReceive]")

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val offsetMillis = intent.getLongExtra(EXTRA_OFFSET_MILLIS, 0L)
        val eventType = inferEventType(title, message)
        val notificationId = generateNotificationIdFromString(idString, offsetMillis)

        // Show notification
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

        // Process repeat scheduling asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                schedulingEngine.processRepeatTrigger(
                    reminderId = idString,
                    offsetMillis = offsetMillis
                )
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Engine repeat-trigger failed for $idString [ReminderReceiver.kt::onReceive]")
            }
        }
    }

    //-------------------------------
    // Helpers
    //-------------------------------

    /**
     * Generates a unique notification ID based on reminder UUID and offset.
     * Ensures ID is always positive and avoids Int.MIN_VALUE edge case.
     */
    private fun generateNotificationIdFromString(idString: String, offsetMillis: Long): Int {
        val raw = idString.hashCode() xor offsetMillis.hashCode()
        return if (raw == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(raw)
    }

    /**
     * Infers the event type (BIRTHDAY, ANNIVERSARY, MEDICINE, WORKOUT, MEETING, GENERAL)
     * based on keywords found in the title or message.
     */
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
