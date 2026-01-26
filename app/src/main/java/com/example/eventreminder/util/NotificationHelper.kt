package com.example.eventreminder.util

// =============================================================
// NotificationHelper — Category-based channels (UUID-safe)
// Sound is bound to CHANNEL (Android O+ compliant)
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

// 🔕 Silent restore channel (NEW, isolated)
private const val CH_RESTORE_SILENT = "channel_restore_silent"

/**
 * NotificationHelper
 *
 * - Category-based notification channels
 * - Deterministic channel sound (Android O+ safe)
 * - UUID always propagated for secure navigation
 * - Silent restore handled via dedicated channel
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
        extras: Map<String, Any?> = emptyMap(),
        silent: Boolean = false
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val uuid = extras[ReminderReceiver.EXTRA_REMINDER_ID_STRING] as? String
        val eventTypeExtra = extras[ReminderReceiver.EXTRA_EVENT_TYPE] as? String

        Timber.tag(TAG).d("🔔 showNotification silent=$silent uuid=$uuid eventType=$eventTypeExtra")

        // ---------------------------------------------------------
        // 🔕 SILENT RESTORE PATH (ISOLATED)
        // ---------------------------------------------------------
        if (silent) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (nm.getNotificationChannel(CH_RESTORE_SILENT) == null) {
                    val silentChannel = NotificationChannel(
                        CH_RESTORE_SILENT,
                        "Restored Notifications (Silent)",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        setSound(null, null)
                        enableVibration(false)
                        enableLights(false)
                        description = "Restored reminders without sound or vibration"
                    }

                    nm.createNotificationChannel(silentChannel)

                    Timber.tag(TAG).d("📡 Created silent restore channel=$CH_RESTORE_SILENT")
                }
            }

            postNotification(
                context = context,
                nm = nm,
                channelId = CH_RESTORE_SILENT,
                notificationId = notificationId,
                title = title,
                message = message,
                extras = extras,
                silent = true
            )
            return
        }

        // ---------------------------------------------------------
        // 🔊 NORMAL (CATEGORY) PATH — UNCHANGED
        // ---------------------------------------------------------
        val channelSound: Uri = when (eventType.uppercase()) {
            "BIRTHDAY" -> Uri.parse("android.resource://${context.packageName}/${R.raw.birthday}")
            "ANNIVERSARY" -> Uri.parse("android.resource://${context.packageName}/${R.raw.anniversary}")
            "MEDICINE" -> Uri.parse("android.resource://${context.packageName}/${R.raw.medicine}")
            "WORKOUT" -> Uri.parse("android.resource://${context.packageName}/${R.raw.workout}")
            else ->
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

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
            if (nm.getNotificationChannel(channelId) == null) {
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

        postNotification(
            context = context,
            nm = nm,
            channelId = channelId,
            notificationId = notificationId,
            title = title,
            message = message,
            extras = extras,
            silent = false,
            sound = channelSound
        )
    }

    // =========================================================
    // INTERNAL POST (shared, deterministic behavior)
    // =========================================================
    private fun postNotification(
        context: Context,
        nm: NotificationManager,
        channelId: String,
        notificationId: Int,
        title: String,
        message: String,
        extras: Map<String, Any?>,
        silent: Boolean,
        sound: Uri? = null
    ) {
        val uuid = extras[ReminderReceiver.EXTRA_REMINDER_ID_STRING] as? String
        val eventTypeExtra =
            (extras[ReminderReceiver.EXTRA_EVENT_TYPE] as? String)
                ?: run {
                    val t = title.lowercase()
                    val m = message.lowercase()
                    when {
                        "birthday" in t -> "BIRTHDAY"
                        "anniversary" in t -> "ANNIVERSARY"
                        "medicine" in t -> "MEDICINE"
                        "workout" in t -> "WORKOUT"
                        "meeting" in t -> "MEETING"
                        "pill" in m || "tablet" in m -> "MEDICINE"
                        "exercise" in m || "gym" in m -> "WORKOUT"
                        "meet" in m -> "MEETING"
                        else -> "GENERAL"
                    }
                }


        // -----------------------------------------------------
        // Tap → MainActivity navigation
        // -----------------------------------------------------
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

        // -----------------------------------------------------
        // Dismiss
        // -----------------------------------------------------
        val dismissIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_DISMISS
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID_STRING, uuid)
            putExtra(
                ReminderReceiver.EXTRA_OFFSET_MILLIS,
                extras[ReminderReceiver.EXTRA_OFFSET_MILLIS] as? Long ?: 0L
            )
        }

        val dismissPI = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(
                if (silent) NotificationCompat.PRIORITY_LOW
                else NotificationCompat.PRIORITY_HIGH
            )
            .setContentIntent(tapPI)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        // =====================================================
        // 🎯 ACTION POLICY
        // =====================================================
        when (eventTypeExtra?.uppercase()) {

            // 🎂 Only birthdays & anniversaries get Open Card
            "BIRTHDAY", "ANNIVERSARY" -> {
                // Open Card
                val openIntent = Intent(context, MainActivity::class.java).apply {
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
                    System.currentTimeMillis().toInt(),   // ✅ UNIQUE requestCode
                    openIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                builder.addAction(R.drawable.ic_open, "Open Card", openPI)
                builder.addAction(R.drawable.ic_close, "Dismiss", dismissPI)
            }

            // -------------------------------------------------
            // ℹ️ Other notifications
            // -------------------------------------------------
            else -> {
                builder.addAction(R.drawable.ic_close, "Dismiss", dismissPI)
            }
        }

        if (!silent && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(sound)
        }

        nm.notify(notificationId, builder.build())

        Timber.tag(TAG).d("📢 Notification posted id=$notificationId channel=$channelId silent=$silent uuid=$uuid type=$eventTypeExtra")
    }
}
