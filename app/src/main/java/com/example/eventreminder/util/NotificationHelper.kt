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

private const val TAG = "NotificationHelper"

private const val CH_BIRTHDAY = "channel_birthday"
private const val CH_ANNIVERSARY = "channel_anniversary"
private const val CH_GENERAL = "channel_general"

object NotificationHelper {

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
        // Extract UUID + eventType (CRITICAL FIX)
        // ---------------------------------------------------------
        val uuid = extras[ReminderReceiver.EXTRA_REMINDER_ID_STRING] as? String
        val eventTypeExtra = extras[ReminderReceiver.EXTRA_EVENT_TYPE] as? String

        Timber.tag(TAG).d("🔥 showNotification() → uuid=$uuid eventType=$eventTypeExtra")

        // ---------------------------------------------------------
        // Smart sound
        // ---------------------------------------------------------
        val resolvedSound: Uri? = RingtoneResolver.resolve(
            context = context,
            title = title,
            message = message
        )
        val finalSound: Uri = resolvedSound
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // ---------------------------------------------------------
        // Create channels
        // ---------------------------------------------------------
        val channelId = when (eventType.uppercase()) {
            "BIRTHDAY" -> CH_BIRTHDAY
            "ANNIVERSARY" -> CH_ANNIVERSARY
            else -> CH_GENERAL
        }

        val channelName = when (eventType.uppercase()) {
            "BIRTHDAY" -> "Birthday Reminders"
            "ANNIVERSARY" -> "Anniversary Reminders"
            else -> "General Reminders"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = nm.getNotificationChannel(channelId)
                ?: NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
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
        // DISMISS
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

        val emoji = when (eventType.uppercase()) {
            "BIRTHDAY" -> "\uD83C\uDF89 "
            "ANNIVERSARY" -> "❤️ "
            else -> ""
        }

        val fullMessage = "$emoji$message"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullMessage))
            .setOngoing(true)
            .setContentIntent(tapPI)
            .addAction(R.drawable.ic_open, "Open Card", openPI)
            .addAction(R.drawable.ic_close, "Dismiss", dismissPI)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 300, 200, 300))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(finalSound)
        }

        nm.notify(notificationId, builder.build())

        Timber.tag(TAG).d("📢 Notification delivered → id=$notificationId uuid=$uuid")
    }
}
