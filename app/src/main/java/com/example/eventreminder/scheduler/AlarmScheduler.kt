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

private const val TAG = "AlarmScheduler"

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

    // -------------------------------------------------------------------------
    // UUID deterministic notification ID
    // -------------------------------------------------------------------------
    private fun generateNotificationId(idString: String, offsetMillis: Long): Int {
        val raw = idString.hashCode() xor offsetMillis.hashCode()
        return if (raw == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(raw)
    }

    private fun requestCodeFor(idString: String, offsetMillis: Long): Int {
        return generateNotificationId(idString, offsetMillis)
    }

    // -------------------------------------------------------------------------
    // UUID PendingIntent Builder
    // -------------------------------------------------------------------------
    private fun buildPIString(
        reminderIdString: String,
        offsetMillis: Long,
        title: String?,
        message: String?,
        repeatRule: String?
    ): PendingIntent {

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.eventreminder.REMIND_${reminderIdString}_$offsetMillis"

            putExtra(ReminderReceiver.EXTRA_REMINDER_ID_STRING, reminderIdString)
            putExtra(ReminderReceiver.EXTRA_OFFSET_MILLIS, offsetMillis)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_MESSAGE, message)
            putExtra(ReminderReceiver.EXTRA_REPEAT_RULE, repeatRule)
        }

        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(reminderIdString, offsetMillis),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getExistingPIString(
        reminderIdString: String,
        offsetMillis: Long
    ): PendingIntent? {

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.example.eventreminder.REMIND_${reminderIdString}_$offsetMillis"
        }

        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(reminderIdString, offsetMillis),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // -------------------------------------------------------------------------
    // UUID scheduleExact
    // -------------------------------------------------------------------------
    fun scheduleExactByString(
        reminderIdString: String,
        eventTriggerMillis: Long,
        offsetMillis: Long,
        title: String?,
        message: String?,
        repeatRule: String?
    ) {
        if (alarmManager == null) return

        val actualTrigger = eventTriggerMillis - offsetMillis
        if (actualTrigger <= System.currentTimeMillis()) {
            Timber.tag(TAG).d("⏭ Offset skipped (past) idString=$reminderIdString off=$offsetMillis")
            return
        }

        val pi = buildPIString(reminderIdString, offsetMillis, title, message, repeatRule)

        val formatted = try {
            Instant.ofEpochMilli(actualTrigger)
                .atZone(ZoneId.systemDefault())
                .format(formatter)
        } catch (_: Exception) {
            actualTrigger.toString()
        }

        Timber.tag(TAG).d(
            "Alarm → scheduleExact (UUID) idString=$reminderIdString off=$offsetMillis at $formatted repeat=$repeatRule"
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                AlarmManagerCompat.setExact(
                    alarmManager,
                    AlarmManager.RTC_WAKEUP,
                    actualTrigger,
                    pi
                )
                return
            }

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

    // -------------------------------------------------------------------------
    // UUID scheduleAll
    // -------------------------------------------------------------------------
    fun scheduleAllByString(
        reminderIdString: String,
        title: String?,
        message: String?,
        repeatRule: String?,
        nextEventTime: Long,
        offsets: List<Long>
    ) {
        offsets.forEach { offsetMillis ->

            scheduleExactByString(
                reminderIdString = reminderIdString,
                eventTriggerMillis = nextEventTime,
                offsetMillis = offsetMillis,
                title = title,
                message = message,
                repeatRule = repeatRule
            )

            val finalTrigger = nextEventTime - offsetMillis

            Timber.tag(TAG).d(
                "scheduleAll (UUID) → idString=$reminderIdString offset=$offsetMillis finalTrigger=$finalTrigger"
            )
        }
    }

    // -------------------------------------------------------------------------
    // UUID cancelAll
    // -------------------------------------------------------------------------
    fun cancelAllByString(reminderIdString: String, offsets: List<Long>) {
        if (alarmManager == null) return

        offsets.forEach { offset ->
            val pi = getExistingPIString(reminderIdString, offset)
            if (pi != null) {
                Timber.tag(TAG).d("Cancel (UUID) → idString=$reminderIdString offset=$offset")
                try {
                    alarmManager.cancel(pi)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed cancel UUID idString=$reminderIdString offset=$offset")
                }
            }
        }
    }
}
