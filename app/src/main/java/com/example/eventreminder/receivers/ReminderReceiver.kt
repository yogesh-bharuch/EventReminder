package com.example.eventreminder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.scheduler.AlarmScheduler
import com.example.eventreminder.util.NextOccurrenceCalculator
import com.example.eventreminder.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_REPEAT_RULE = "extra_repeat_rule"
        const val EXTRA_OFFSET_MILLIS = "offsetMillis"
    }

    @Inject lateinit var repo: ReminderRepository
    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {

        val id = intent.getLongExtra(EXTRA_REMINDER_ID, -1)
        if (id == -1L) return

        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val repeatRule = intent.getStringExtra(EXTRA_REPEAT_RULE)
        val offsetMillis = intent.getLongExtra(EXTRA_OFFSET_MILLIS, 0L)

        // Fire notification
        NotificationHelper.showNotification(context, title, message)

        // Recurring logic → async
        CoroutineScope(Dispatchers.IO).launch {

            val r = repo.getReminder(id) ?: return@launch

            if (repeatRule.isNullOrEmpty()) return@launch // one-time

            // Next event time
            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                r.eventEpochMillis,
                r.timeZone,
                r.repeatRule
            ) ?: return@launch

            // Update DB event time
            repo.update(r.copy(eventEpochMillis = nextEvent))

            // Reschedule all offsets
            val offsets = r.reminderOffsets.ifEmpty { listOf(0L) }

            scheduler.scheduleAll(
                reminderId = id,
                title = r.title,
                message = r.description ?: "",
                repeatRule = r.repeatRule,
                nextEventTime = nextEvent,
                offsets = offsets
            )

            Timber.tag("ReminderReceiver")
                .d("Rescheduled recurring id=$id for nextEvent=${Instant.ofEpochMilli(nextEvent)}")
        }
    }
}
