package com.example.eventreminder.util

// =============================================================
// NotificationHelper — Category-based channels (UUID-safe)
// Sound is bound to CHANNEL (Android O+ compliant)
// =============================================================

// =============================================================
// Imports
// =============================================================
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.eventreminder.MainActivity
import com.example.eventreminder.R
import com.example.eventreminder.receivers.ReminderReceiver
import timber.log.Timber

// =============================================================
// Constants
// =============================================================
private const val TAG = "NotificationHelper"

// Category-based channels
private const val CH_BIRTHDAY = "channel_birthday"
private const val CH_ANNIVERSARY = "channel_anniversary"
private const val CH_MEDICINE = "channel_medicine"
private const val CH_WORKOUT = "channel_workout"
private const val CH_GENERAL = "channel_general"

/**
 * NotificationHelper
 *
 * - Category-based notification channels
 * - Deterministic channel sound (Android O+ safe)
 * - UUID always propagated for secure navigation
 */
object NotificationHelper {

    // ---------------------------------------------------------
    // Helper → DRY extras propagation
    // ---------------------------------------------------------
    private fun Intent.putExtrasFromMap(extras: Map<String, Any?>) {
        extras.forEach { (key, value) ->
            when (value) {
                is Boolean -> putExtra(key, value)
                is Long -> putExtra(key, value)
                is String -> putExtra(key, value)
                is Int -> putExtra(key, value)
            }
        }
    }

    fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        eventType: String = "UNKNOWN",
        extras: Map<String, Any?> = emptyMap()
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)

        // ---------------------------------------------------------
        // Deterministic sound per event type (CHANNEL SAFE)
        // ---------------------------------------------------------
        val channelSound: Uri = when (eventType.uppercase()) {
            "BIRTHDAY" ->
                Uri.parse("android.resource://${context.packageName}/${R.raw.birthday}")
            "ANNIVERSARY" ->
                Uri.parse("android.resource://${context.packageName}/${R.raw.anniversary}")
            "MEDICINE" ->
                Uri.parse("android.resource://${context.packageName}/${R.raw.medicine}")
            "WORKOUT" ->
                Uri.parse("android.resource://${context.packageName}/${R.raw.workout}")
            else ->
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        val uuid = extras[ReminderReceiver.EXTRA_REMINDER_ID_STRING] as? String
        val eventTypeExtra = extras[ReminderReceiver.EXTRA_EVENT_TYPE] as? String

        Timber.tag(TAG).d("🔔 showNotification → uuid=$uuid eventType=$eventTypeExtra")

        // ---------------------------------------------------------
        // Channel mapping
        // ---------------------------------------------------------
        val channelId = when (eventType.uppercase()) {
            "BIRTHDAY" -> CH_BIRTHDAY
            "ANNIVERSARY" -> CH_ANNIVERSARY
            "MEDICINE" -> CH_MEDICINE
            "WORKOUT" -> CH_WORKOUT
            else -> CH_GENERAL
        }

        val channelName = when (eventType.uppercase()) {
            "BIRTHDAY" -> "Birthday Reminders"
            "ANNIVERSARY" -> "Anniversary Reminders"
            "MEDICINE" -> "Medicine Reminders"
            "WORKOUT" -> "Workout Reminders"
            else -> "General Reminders"
        }

        // ---------------------------------------------------------
        // Create notification channel (Android O+)
        // NOTE: Sound is immutable once channel exists
        // ---------------------------------------------------------
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = nm.getNotificationChannel(channelId)

            if (existing == null) {
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(
                        channelSound,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    enableVibration(true)
                    description = "Reminder notifications"
                }

                nm.createNotificationChannel(channel)

                Timber.tag(TAG).d("📡 Created channel=$channelId sound=$channelSound")
            }
        }

        // =========================================================
        // TAP → OPEN MAIN ACTIVITY (UUID SAFE)
        // =========================================================
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtrasFromMap(extras)
            putExtra(ReminderReceiver.EXTRA_FROM_NOTIFICATION, true)
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID_STRING, uuid)
            putExtra(ReminderReceiver.EXTRA_EVENT_TYPE, eventTypeExtra)
        }

        val tapPI = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // =========================================================
        // OPEN CARD ACTION
        // =========================================================
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ReminderReceiver.ACTION_OPEN_CARD
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtrasFromMap(extras)
            putExtra(ReminderReceiver.EXTRA_FROM_NOTIFICATION, true)
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID_STRING, uuid)
            putExtra(ReminderReceiver.EXTRA_EVENT_TYPE, eventTypeExtra)
        }

        val openPI = PendingIntent.getActivity(
            context,
            notificationId + 1,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // =========================================================
        // DISMISS ACTION (UI only)
        // =========================================================
        val dismissIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_DISMISS
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }

        val dismissPI = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // ---------------------------------------------------------
        // Emoji prefix (presentation only)
        // ---------------------------------------------------------
        val emojiPrefix = when (eventType.uppercase()) {
            "BIRTHDAY" -> "🎂 "
            "ANNIVERSARY" -> "❤️ "
            "MEDICINE" -> "💊 "
            "MEETING" -> "📅 "
            "WORKOUT" -> "💪 "
            else -> ""
        }

        val fullMessage = "$emojiPrefix$message"

        // ---------------------------------------------------------
        // Build notification
        // ---------------------------------------------------------
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(fullMessage)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapPI)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullMessage))
            .addAction(R.drawable.ic_open, "Open Card", openPI)
            .addAction(R.drawable.ic_close, "Dismiss", dismissPI)

        // Pre-O devices → runtime sound
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(channelSound)
        }

        // ---------------------------------------------------------
        // Post notification
        // ---------------------------------------------------------
        nm.notify(notificationId, builder.build())

        Timber.tag(TAG).d("📢 Notification posted id=$notificationId channel=$channelId uuid=$uuid")
    }
}
