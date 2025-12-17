package com.example.eventreminder.scheduler.elapsed

import com.example.eventreminder.data.model.EventReminder

/**
 * Handles detection and disabling of elapsed one-time reminders.
 *
 * Scheduling-engine owned.
 */
interface ElapsedReminderHandler {

    /**
     * Checks a reminder and disables it if it is an elapsed one-time reminder.
     *
     * @param reminder Reminder entity
     * @param nowEpochMillis Current wall-clock time
     *
     * @return true if reminder was disabled, false otherwise
     */
    suspend fun handleIfElapsed(
        reminder: EventReminder,
        nowEpochMillis: Long
    ): Boolean
}
