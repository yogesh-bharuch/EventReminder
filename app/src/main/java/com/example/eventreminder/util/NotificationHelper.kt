package com.example.eventreminder.util

// =============================================================
// NotificationHelper — FIXED Open Card Navigation (UUID Always Sent)
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
import com.example.eventreminder.ui.notification.RingtoneResolver
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
 * Category-based channels + smart ringtone + actions (Open Card + Dismiss)
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
        // SMART SOUND SELECTOR (single resolution)
        // ---------------------------------------------------------
        val resolvedSound: Uri? = RingtoneResolver.resolve(context, title, message)
        val finalSound: Uri = resolvedSound
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val uuid = extras[ReminderReceiver.EXTRA_REMINDER_ID_STRING] as? String
        val eventTypeExtra = extras[ReminderReceiver.EXTRA_EVENT_TYPE] as? String

        Timber.tag(TAG).d("🔥 showNotification() → uuid=$uuid eventType=$eventTypeExtra")

        // ---------------------------------------------------------
        // Create channels
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = nm.getNotificationChannel(channelId)
                ?: NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH // ensure high importance
                ).apply {
                    setSound(
                        finalSound,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    enableVibration(true)
                    description = "Reminder notifications"
                }

            nm.createNotificationChannel(channel)
        }

        // =========================================================
        // TAP → OPEN MAIN ACTIVITY AND NAVIGATE TO CARD
        // ALWAYS INCLUDES UUID
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
        // OPEN CARD ACTION (CRITICAL FIX: UUID ADDED)
        // =========================================================
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ReminderReceiver.ACTION_OPEN_CARD

            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtrasFromMap(extras)

            // FIX: Always add required navigation extras
            putExtra(ReminderReceiver.EXTRA_FROM_NOTIFICATION, true)
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID_STRING, uuid)
            putExtra(ReminderReceiver.EXTRA_EVENT_TYPE, eventTypeExtra)

            Timber.tag(TAG).d("🔥 OpenCard Intent → uuid=$uuid")
        }

        val openPI = PendingIntent.getActivity(
            context,
            notificationId + 1,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // =========================================================
        // DISMISS ACTION
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
        // EMOJI + MESSAGE
        // ---------------------------------------------------------
        val emojiPrefix = when (eventType.uppercase()) {
            "BIRTHDAY" -> "🎂 "
            "ANNIVERSARY" -> "❤️ "
            "MEDICINE" -> "💊 "
            "WORKOUT" -> "💪 "
            else -> ""
        }

        val fullMessage = "$emojiPrefix$message"

        // ---------------------------------------------------------
        // BUILD NOTIFICATION 😀 😃
        // ---------------------------------------------------------
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
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
            builder.setSound(finalSound)
        }

        // ---------------------------------------------------------
        // POST NOTIFICATION
        // ---------------------------------------------------------
        nm.notify(notificationId, builder.build())

        Timber.tag(TAG).d("Notification posted → id=$notificationId channel=$channelId")
        Timber.tag(TAG).d("📢 Notification delivered → id=$notificationId uuid=$uuid")
    }
}