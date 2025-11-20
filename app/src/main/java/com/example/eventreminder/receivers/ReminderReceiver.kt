package com.example.eventreminder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.scheduler.AlarmScheduler
import com.example.eventreminder.util.NextOccurrenceCalculator
import com.example.eventreminder.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

// =============================================================
// Constants
// =============================================================
private const val TAG = "ReminderReceiver"

/**
 * ReminderReceiver
 *
 * Handles alarm triggers → shows notification + reschedules next event.
 * NOW enhanced for TODO-7:
 *  - Notification tap will pass data into MainActivity for Card Generator flow.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_REPEAT_RULE = "extra_repeat_rule"
        const val EXTRA_OFFSET_MILLIS = "offsetMillis"

        // NEW — Card-generation routing extras (TODO-7)
        const val EXTRA_FROM_NOTIFICATION = "from_notification"
        const val EXTRA_EVENT_TYPE = "event_type"  // BIRTHDAY / ANNIVERSARY
    }

    @Inject lateinit var repo: ReminderRepository
    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {

        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        if (id == -1L) return

        val title   = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val repeatRule   = intent.getStringExtra(EXTRA_REPEAT_RULE)
        val offsetMillis = intent.getLongExtra(EXTRA_OFFSET_MILLIS, 0L)

        // =============================================================
        // Show notification → NOW includes deep-link extras
        // =============================================================
        NotificationHelper.showNotification(
            context = context,
            title = title,
            message = message,
            extras = mapOf(
                EXTRA_FROM_NOTIFICATION to true,
                EXTRA_REMINDER_ID to id,
                EXTRA_EVENT_TYPE to inferEventType(title, message) // NEW helper
            )
        )

        // =============================================================
        // Recurring Scheduling
        // =============================================================
        CoroutineScope(Dispatchers.IO).launch {

            val r = repo.getReminder(id) ?: return@launch

            if (repeatRule.isNullOrEmpty()) return@launch   // no repeat → done

            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                r.eventEpochMillis,
                r.timeZone,
                r.repeatRule
            ) ?: return@launch

            repo.update(r.copy(eventEpochMillis = nextEvent))

            val offsets = r.reminderOffsets.ifEmpty { listOf(0L) }

            scheduler.scheduleAll(
                reminderId = id,
                title = r.title,
                message = r.description ?: "",
                repeatRule = r.repeatRule,
                nextEventTime = nextEvent,
                offsets = offsets
            )

            Timber.tag(TAG).d(
                "Rescheduled recurring id=$id → next=${Instant.ofEpochMilli(nextEvent)}"
            )
        }
    }

    // =============================================================
    // NEW (TODO-7) — Helper to infer card type from title/message
    // =============================================================
    private fun inferEventType(title: String, message: String): String {
        val raw = "$title $message".lowercase()

        return when {
            raw.contains("birthday")     -> "BIRTHDAY"
            raw.contains("anniversary")  -> "ANNIVERSARY"
            else                         -> "UNKNOWN"
        }
    }
}
