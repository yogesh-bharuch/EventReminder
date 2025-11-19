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

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repo: ReminderRepository
    @Inject lateinit var scheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {

        if (
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {

            CoroutineScope(Dispatchers.IO).launch {

                try {
                    val reminders = repo.getAllOnce()
                    val now = Instant.now().toEpochMilli()

                    reminders.forEach { reminder ->

                        if (!reminder.enabled) return@forEach

                        val offsets = reminder.reminderOffsets.ifEmpty { listOf(0L) }

                        offsets.forEach { offsetMillis ->

                            val triggerAt = reminder.eventEpochMillis - offsetMillis
                            val missed = triggerAt < now

                            // ------------------------------------------
                            // ONE-TIME MISSED → Fire immediately
                            // ------------------------------------------
                            if (missed && reminder.repeatRule.isNullOrEmpty()) {
                                NotificationHelper.showNotification(
                                    context,
                                    reminder.title,
                                    reminder.description.orEmpty()
                                )

                                Timber.tag("BootReceiver").d("Fired missed one-time id=${reminder.id} offset=$offsetMillis")

                                return@forEach
                            }

                            // ------------------------------------------
                            // RECURRING MISSED → Fire now, schedule next
                            // ------------------------------------------
                            val actualTrigger = if (missed) {

                                NotificationHelper.showNotification(
                                    context,
                                    reminder.title,
                                    reminder.description.orEmpty()
                                )

                                Timber.tag("BootReceiver").d("Fired missed recurring id=${reminder.id} offset=$offsetMillis")

                                val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                                    reminder.eventEpochMillis,
                                    reminder.timeZone,
                                    reminder.repeatRule
                                ) ?: return@forEach

                                nextEvent - offsetMillis

                            } else triggerAt

                            // ------------------------------------------
                            // RESCHEDULE THIS OFFSET
                            // ------------------------------------------
                            scheduler.scheduleExact(
                                reminderId = reminder.id,
                                eventTriggerMillis = actualTrigger + offsetMillis,
                                offsetMillis = offsetMillis,
                                title = reminder.title,
                                message = reminder.description.orEmpty(),
                                repeatRule = reminder.repeatRule
                            )

                            Timber.tag("BootReceiver").d("Scheduled id=${reminder.id} offset=$offsetMillis at=$actualTrigger")
                        }
                    }

                } catch (e: Exception) {
                    Timber.tag("BootReceiver").e(e, "Failed to reschedule reminders on boot")
                }
            }
        }
    }
}
