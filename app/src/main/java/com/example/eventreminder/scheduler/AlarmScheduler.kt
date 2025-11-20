package com.example.eventreminder.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.AlarmManagerCompat
import com.example.eventreminder.receivers.ReminderReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

// =============================================================
// Constants
// =============================================================
private const val TAG = "AlarmScheduler"

/**
 * AlarmScheduler
 *
 * Schedules exact alarms for all reminder offsets.
 * This class DOES NOT handle notification tap intents.
 * (Handled inside ReminderReceiver → NotificationHelper)
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // System alarm manager
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    // For readable logging
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

    // =============================================================
    // Request Code Generator
    // =============================================================

    /**
     * Generate stable request code based on reminderId + offsetMillis.
     */
    private fun requestCodeFor(reminderId: Long, offsetMillis: Long): Int {
        val mix = reminderId * 31 + offsetMillis
        return (mix xor (mix ushr 32)).toInt()
    }

    // =============================================================
    // PendingIntent Builder (internal)
    // =============================================================

    /**
     * Build the PendingIntent that will trigger ReminderReceiver.
     * Contains metadata: id, title, message, repeat rule, offset.
     */
    private fun buildPI(
        reminderId: Long,
        offsetMillis: Long,
        title: String?,
        message: String?,
        repeatRule: String?
    ): PendingIntent {

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.eventreminder.REMIND_${reminderId}_$offsetMillis"

            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderReceiver.EXTRA_OFFSET_MILLIS, offsetMillis)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_MESSAGE, message)
            putExtra(ReminderReceiver.EXTRA_REPEAT_RULE, repeatRule)
        }

        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(reminderId, offsetMillis),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Retrieve existing PI (for cancellation).
     */
    private fun getExistingPI(reminderId: Long, offsetMillis: Long): PendingIntent? {

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.eventreminder.REMIND_${reminderId}_$offsetMillis"
        }

        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(reminderId, offsetMillis),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // =============================================================
    // Single Alarm Scheduler
    // =============================================================

    /**
     * Schedule one exact alarm for one offset.
     */
    fun scheduleExact(
        reminderId: Long,
        eventTriggerMillis: Long,
        offsetMillis: Long,
        title: String?,
        message: String?,
        repeatRule: String?
    ) {
        if (alarmManager == null) return

        val actualTrigger = eventTriggerMillis - offsetMillis

        // Skip if already in past
        if (actualTrigger <= System.currentTimeMillis()) {
            Timber.tag(TAG).d("⏭ Offset skipped (past) id=$reminderId off=$offsetMillis")
            return
        }

        val pi = buildPI(reminderId, offsetMillis, title, message, repeatRule)

        val readable = try {
            Instant.ofEpochMilli(actualTrigger)
                .atZone(ZoneId.systemDefault())
                .format(formatter)
        } catch (_: Exception) {
            actualTrigger.toString()
        }

        Timber.tag(TAG).d(
            "Alarm → scheduleExact id=$reminderId off=$offsetMillis at $readable repeat=$repeatRule"
        )

        try {

            // Android 12+ exact alarm permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                Timber.tag(TAG).w("Exact alarm permission missing → fallback setExact()")

                AlarmManagerCompat.setExact(
                    alarmManager,
                    AlarmManager.RTC_WAKEUP,
                    actualTrigger,
                    pi
                )
                return
            }

            // Normal path
            AlarmManagerCompat.setExactAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                actualTrigger,
                pi
            )

        } catch (se: SecurityException) {
            Timber.tag(TAG).e(se, "SecurityException → fallback inexact")
            alarmManager.set(AlarmManager.RTC_WAKEUP, actualTrigger, pi)
        }
    }

    // =============================================================
    // Cancel All Alarms
    // =============================================================

    fun cancelAll(reminderId: Long, offsets: List<Long>) {
        if (alarmManager == null) return

        offsets.forEach { offset ->
            val pi = getExistingPI(reminderId, offset)
            if (pi != null) {
                Timber.tag(TAG).d("Cancel → id=$reminderId offset=$offset")
                try {
                    alarmManager.cancel(pi)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to cancel alarm id=$reminderId offset=$offset")
                }
            }
        }
    }

    // =============================================================
    // Schedule All Offsets
    // =============================================================

    /**
     * Schedule all alarms for the given reminder's next event time.
     */
    fun scheduleAll(
        reminderId: Long,
        title: String?,
        message: String?,
        repeatRule: String?,
        nextEventTime: Long,
        offsets: List<Long>
    ) {
        offsets.forEach { offsetMillis ->
            val finalTrigger = nextEventTime - offsetMillis

            scheduleExact(
                reminderId = reminderId,
                eventTriggerMillis = nextEventTime,
                offsetMillis = offsetMillis,
                title = title,
                message = message,
                repeatRule = repeatRule
            )

            Timber.tag(TAG).d(
                "scheduleAll → id=$reminderId offset=$offsetMillis finalTrigger=$finalTrigger"
            )
        }
    }
}
