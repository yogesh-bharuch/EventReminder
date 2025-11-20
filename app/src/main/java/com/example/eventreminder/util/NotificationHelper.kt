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

/**
 * NotificationHelper
 *
 * Now enhanced for TODO-7:
 * - Adds deep-link PendingIntent
 * - Passes extras for card generation flow
 */
object NotificationHelper {

    /**
     * Show reminder notification with deep link into MainActivity.
     *
     * @param extras additional routing data:
     *     - from_notification: Boolean
     *     - reminder_id: Long
     *     - event_type: BIRTHDAY / ANNIVERSARY
     */
    fun showNotification(
        context: Context,
        title: String,
        message: String,
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
                "Event Reminders",
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
        // Build deep-link intent → MainActivity
        // ---------------------------------------------------------
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

            // ⭐ Pass routing extras (TODO-7)
            extras.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putExtra(key, value)
                    is Long -> putExtra(key, value)
                    is String -> putExtra(key, value)
                }
            }
        }

        val pendingTap = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        Timber.tag(TAG).d("Notification → extras: $extras")

        // ---------------------------------------------------------
        // Build notification
        // ---------------------------------------------------------
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)                    // Title of the notification
            .setContentText(message)                  // Body text
            .setSmallIcon(R.drawable.ic_notification) // Icon shown in status bar
            .setAutoCancel(true)                      // Dismiss when tapped
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for heads-up
            .setSound(soundUri)                       // Custom sound (pre-Oreo)
            .setVibrate(longArrayOf(0, 250, 250, 250))// Vibration pattern
            .setContentIntent(pendingTap)             // Deep link or action
            .build()

        // ---------------------------------------------------------
        // Display notification
        // ---------------------------------------------------------
        nm.notify(System.currentTimeMillis().toInt(), notification)

        //logging
        val notificationId = System.currentTimeMillis().toInt()
        val timestamp = java.text.SimpleDateFormat("dd MMM yyyy, h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date())
        Timber.tag(TAG).d("Notification posted → ID: $notificationId | Time: $timestamp | Title: \"$title\" | Message: \"$message\"")
    }
}
