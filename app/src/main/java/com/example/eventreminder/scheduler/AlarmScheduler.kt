package com.example.eventreminder.scheduler

// =============================================================
// AlarmScheduler — UUID-ONLY Version (Final Clean Implementation)
//
// Responsibilities:
//  • Schedules exact alarms (UUID-based)
//  • Cancels alarms
//  • Builds PendingIntents with deterministic request codes
//
// Project Standards:
//  • Named arguments everywhere
//  • Section headers
//  • Inline comments explaining each step
//  • UUID only — Long IDs removed
// =============================================================

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

    // System Alarm manager
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    // Human-readable logging format
    private val formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

    // =============================================================
    // Deterministic UUID → Notification ID
    // =============================================================
    private fun generateNotificationId(
        idString: String,
        offsetMillis: Long
    ): Int {
        val raw = idString.hashCode() xor offsetMillis.hashCode()
        return if (raw == Int.MIN_VALUE) Int.MAX_VALUE else kotlin.math.abs(raw)
    }

    private fun requestCodeFor(
        idString: String,
        offsetMillis: Long
    ): Int = generateNotificationId(
        idString = idString,
        offsetMillis = offsetMillis
    )

    // =============================================================
    // Build PendingIntent (UUID version)
    // =============================================================
    private fun buildPIString(
        reminderIdString: String,
        offsetMillis: Long,
        title: String?,
        message: String?,
        repeatRule: String?
    ): PendingIntent {

        val intent = Intent(
            /* packageContext = */ context,
            /* cls = */ ReminderReceiver::class.java
        ).apply {

            // Each UUID+offset has unique action
            action = "com.example.eventreminder.REMIND_${reminderIdString}_$offsetMillis"

            // Required extras
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID_STRING, reminderIdString)
            putExtra(ReminderReceiver.EXTRA_OFFSET_MILLIS, offsetMillis)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_MESSAGE, message)
            putExtra(ReminderReceiver.EXTRA_REPEAT_RULE, repeatRule)
        }

        return PendingIntent.getBroadcast(
            /* context = */ context,
            /* requestCode = */ requestCodeFor(idString = reminderIdString, offsetMillis = offsetMillis),
            /* intent = */ intent,
            /* flags = */ PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // =============================================================
    // Retrieve existing PendingIntent (UUID version)
    // =============================================================
    private fun getExistingPIString(
        reminderIdString: String,
        offsetMillis: Long
    ): PendingIntent? {

        val intent = Intent(
            context,
            ReminderReceiver::class.java
        ).apply {
            action = "com.example.eventreminder.REMIND_${reminderIdString}_$offsetMillis"
        }

        return PendingIntent.getBroadcast(
            /* context = */ context,
            /* requestCode = */ requestCodeFor(idString = reminderIdString, offsetMillis = offsetMillis),
            /* intent = */ intent,
            /* flags = */ PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // =============================================================
    // scheduleExactByString — Core UUID Scheduling
    // =============================================================
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

        // Skip expired offsets
        if (actualTrigger <= System.currentTimeMillis()) {
            Timber.tag(TAG).d("⏭ Skipped offset (past) idString=$reminderIdString off=$offsetMillis")
            return
        }

        val pi = buildPIString(
            reminderIdString = reminderIdString,
            offsetMillis = offsetMillis,
            title = title,
            message = message,
            repeatRule = repeatRule
        )

        val readable = try {
            Instant.ofEpochMilli(actualTrigger)
                .atZone(ZoneId.systemDefault())
                .format(formatter)
        } catch (_: Exception) {
            actualTrigger.toString()
        }

        Timber.tag(TAG).d(
            "⏰ scheduleExact(UUID) idString=$reminderIdString off=$offsetMillis at $readable repeat=$repeatRule"
        )

        try {
            // Android 12+ restrictions
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

        } catch (ex: SecurityException) {
            Timber.tag(TAG).e(ex, "⚠ SecurityException — fallback inexact alarm")
            alarmManager.set(AlarmManager.RTC_WAKEUP, actualTrigger, pi)
        }
    }

    // =============================================================
    // scheduleAllByString — Schedules all offsets
    // =============================================================
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
            Timber.tag("SaveReminderLogs")
                .d("🟢 ScheduleAlarm → id=$reminderIdString offset=$offsetMillis finalTrigger=$finalTrigger")
            Timber.tag(TAG).d(
                "📌 scheduleAll(UUID) → idString=$reminderIdString offset=$offsetMillis finalTrigger=$finalTrigger"
            )
        }
    }

    // =============================================================
    // cancelAllByString — Cancels all offsets for a UUID reminder
    // =============================================================
    fun cancelAllByString(
        reminderIdString: String,
        offsets: List<Long>
    ) {

        if (alarmManager == null) return

        offsets.forEach { offset ->
            Timber.tag("SaveReminderLogs").d("🔴 CancelAlarm → id=$reminderIdString offset=$offset")
            val pi = getExistingPIString(
                reminderIdString = reminderIdString,
                offsetMillis = offset
            )

            if (pi != null) {
                Timber.tag(TAG).d("❌ Cancel(UUID) → idString=$reminderIdString offset=$offset")
                try {
                    alarmManager.cancel(pi)
                } catch (ex: Exception) {
                    Timber.tag(TAG).e(ex, "Failed cancel UUID: $reminderIdString offset=$offset")
                }
            }
        }
    }
}
