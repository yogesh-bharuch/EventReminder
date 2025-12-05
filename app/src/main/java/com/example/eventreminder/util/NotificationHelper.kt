package com.example.eventreminder.util

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
        // SMART SOUND SELECTOR
        // ---------------------------------------------------------
        val categorySound: Uri? = RingtoneResolver.resolve(context, title, message)
        val defaultSound: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val finalSound = categorySound ?: defaultSound

        Timber.tag(TAG).d("Sound resolver → $finalSound")

        // ---------------------------------------------------------
        // PICK CHANNEL BY EVENT TYPE
        // ---------------------------------------------------------
        val channelId = when (eventType.uppercase()) {
            "BIRTHDAY" -> CH_BIRTHDAY
            "ANNIVERSARY" -> CH_ANNIVERSARY
            "MEDICINE" -> CH_ANNIVERSARY
            "WORKOUT" -> CH_ANNIVERSARY
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
        // CREATE CHANNEL (Android O+)
        // ---------------------------------------------------------
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val existing = nm.getNotificationChannel(channelId)

            if (existing == null) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val ch = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    setSound(finalSound, attrs)
                    description = "Reminder notifications"
                    enableVibration(true)
                }

                nm.createNotificationChannel(ch)
                Timber.tag(TAG).e("Created NEW channel=$channelId sound=$finalSound")

            } else {
                Timber.tag(TAG).e("Channel EXISTS=$channelId sound=${existing.sound}")
            }
        }

        // ---------------------------------------------------------
        // TAP INTENT → OPEN MAIN ACTIVITY + ROUTE TO CARDSCREEN
        // ---------------------------------------------------------
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

            extras.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putExtra(key, value)
                    is Long -> putExtra(key, value)
                    is String -> putExtra(key, value)
                    is Int -> putExtra(key, value)
                }
            }
        }

        val tapPI = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // ---------------------------------------------------------
        // OPEN CARD ACTION
        // ---------------------------------------------------------
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ReminderReceiver.ACTION_OPEN_CARD
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            extras.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putExtra(key, value)
                    is Long -> putExtra(key, value)
                    is String -> putExtra(key, value)
                    is Int -> putExtra(key, value)
                }
            }
        }

        val openPI = PendingIntent.getActivity(
            context,
            notificationId + 1,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // ---------------------------------------------------------
        // DISMISS ACTION
        // ---------------------------------------------------------
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
            "BIRTHDAY" -> "\uD83C\uDF89 "
            "ANNIVERSARY" -> "\u2764\uFE0F "
            else -> ""
        }

        val fullMessage = "$emojiPrefix$message"

        // ---------------------------------------------------------
        // BUILD NOTIFICATION
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
    }
}
