package com.example.eventreminder.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
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
private const val CHANNEL_ID = "event_channel"
private const val CHANNEL_NAME = "Event Reminders"

/**
 * NotificationHelper
 *
 * Builds and posts notifications with advanced styling and actions:
 *  - Emoji-rich BigText for birthdays/anniversaries
 *  - Open Card & Dismiss action buttons
 *  - Non-auto-cancel / persistent (not dismissable by swipe)
 *
 * NOTE: showNotification is a synchronous helper invoked from BroadcastReceiver.
 */
object NotificationHelper {

    /**
     * Show reminder notification with deep link into MainActivity.
     *
     * @param notificationId deterministic id for later cancellation
     * @param eventType optional: "BIRTHDAY" / "ANNIVERSARY" / "UNKNOWN"
     * @param extras routing extras that will be forwarded to MainActivity when opening card
     */
    fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        eventType: String = "UNKNOWN",
        extras: Map<String, Any?> = emptyMap()
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ---------------------------------------------------------
        // Notification sound
        // ---------------------------------------------------------
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // ---------------------------------------------------------
        // Create channel (Android O+)
        // ---------------------------------------------------------
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for your events"
                setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        // ---------------------------------------------------------
        // Build deep-link intent → MainActivity (tap on notification)
        // Use explicit MainActivity + extras so OS can recreate app and route correctly.
        // ---------------------------------------------------------
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            // ensure we route to existing activity if available, otherwise start fresh
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

            // Forward routing extras for CardScreen
            extras.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putExtra(key, value)
                    is Long -> putExtra(key, value)
                    is String -> putExtra(key, value)
                    is Int -> putExtra(key, value)
                }
            }
        }

        val tapPendingIntent = PendingIntent.getActivity(
            context,
            // stable request code derived from notificationId to ensure uniqueness across pending intents
            notificationId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // ---------------------------------------------------------
        // Action: Open Card (same as tap)
        // ---------------------------------------------------------
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ReminderReceiver.ACTION_OPEN_CARD
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            extras.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putExtra(key, value)
                    is Long -> putExtra(key, value)
                    is String -> putExtra(key, value)
                    is Int -> putExtra(key, value)
                }
            }
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            // different request code to avoid collision with tapPendingIntent
            notificationId + 1,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // ---------------------------------------------------------
        // Action: Dismiss — handled by ReminderReceiver (Broadcast)
        // ---------------------------------------------------------
        val dismissIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_DISMISS
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            // another distinct request code
            notificationId + 2,
            dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        Timber.tag(TAG).d("Notification → extras: $extras | notificationId=$notificationId")

        // ---------------------------------------------------------
        // Build rich content (emoji-aware). Use BigTextStyle for readability.
        // ---------------------------------------------------------
        val emojiPrefix = when (eventType.uppercase()) {
            "BIRTHDAY" -> "\uD83C\uDF89 "   // 🎉
            "ANNIVERSARY" -> "\u2764\uFE0F " // ❤️
            else -> ""
        }
        val fullMessage = "$emojiPrefix$message"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            // IMPORTANT: do not auto-cancel — we want the user to explicitly act.
            .setAutoCancel(false)
            // Make it not dismissable by swipe to satisfy requirement (user must tap action).
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setContentIntent(tapPendingIntent)
            // Add action buttons
            .addAction(
                R.drawable.ic_open, // small icon resource (ensure icon exists)
                "Open Card",
                openPendingIntent
            )
            .addAction(
                R.drawable.ic_close, // small icon resource (ensure icon exists)
                "Dismiss",
                dismissPendingIntent
            )
            // BigText style for expanded view
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(fullMessage)
            )

        // ---------------------------------------------------------
        // Post notification
        // ---------------------------------------------------------
        nm.notify(notificationId, builder.build())

        Timber.tag(TAG).d(
            "Notification posted → ID: $notificationId | Title: \"$title\" | Message: \"$message\" | eventType=$eventType"
        )
    }
}
