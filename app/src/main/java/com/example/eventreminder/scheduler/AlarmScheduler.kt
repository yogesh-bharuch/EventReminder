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

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

    // stable request code derived from reminderId + offsetMillis
    private fun requestCodeFor(reminderId: Long, offsetMillis: Long): Int {
        val mix = reminderId * 31 + offsetMillis
        return (mix xor (mix ushr 32)).toInt()
    }

    // build PI carrying important metadata
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

    // retrieve existing PI (NO_CREATE) for cancellation check
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

    /**
     * Public: scheduleExact using eventTriggerMillis (the event's occurrence instant in millis),
     * and offsetMillis (how many millis before event to trigger).
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
        if (actualTrigger <= System.currentTimeMillis()) {
            Timber.tag("ReminderReceiver")
                .d("⏭ Offset skipped (in past) id=$reminderId off=$offsetMillis")
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

        Timber.tag("ReminderReceiver")
            .d("Alarm → Scheduling id=$reminderId offset=$offsetMillis at $readable (repeat=$repeatRule)")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                Timber.tag("ReminderReceiver")
                    .w("Missing exact alarm permission → fallback setExact")

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
            Timber.tag("ReminderReceiver")
                .e(se, "SecurityException → fallback inexact")

            alarmManager.set(AlarmManager.RTC_WAKEUP, actualTrigger, pi)
        }
    }

    /**
     * Cancel ALL alarms for this reminder by iterating offsets list.
     * This is robust if offsets changed between saves.
     */
    fun cancelAll(reminderId: Long, offsets: List<Long>) {
        if (alarmManager == null) return
        offsets.forEach { off ->
            val pi = getExistingPI(reminderId, off)
            if (pi != null) {
                Timber.tag("ReminderReceiver").d("Cancel → id=$reminderId offset=$off")
                try {
                    alarmManager.cancel(pi)
                } catch (e: Exception) {
                    Timber.tag("ReminderReceiver").e(e, "Alarm: Failed to cancel id=$reminderId offset=$off")
                }
            }
        }
    }

    /**
     * Schedule ALL reminder offsets for the next event time.
     *
     * nextEventTime = event time in UTC millis (no offset subtraction)
     * offsets = list of offsets in milliseconds (e.g. 0, 3600000, etc.)
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

            val triggerAt = nextEventTime - offsetMillis

            scheduleExact(
                reminderId = reminderId,
                eventTriggerMillis = nextEventTime,
                offsetMillis = offsetMillis,
                title = title,
                message = message,
                repeatRule = repeatRule
            )

            Timber.tag("ReminderReceiver").d(
                "scheduleAll → id=$reminderId offset=$offsetMillis finalTrigger=$triggerAt"
            )
        }
    }

}
