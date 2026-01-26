
package com.example.eventreminder.scheduler.elapsed

import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.logging.DELETE_TAG
import timber.log.Timber
import javax.inject.Inject

/**
 * Default implementation of ElapsedReminderHandler.
 *
 * Scheduling-engine owned.
 */
class ElapsedReminderHandlerImpl @Inject constructor(
    private val reminderRepository: ReminderRepository
) : ElapsedReminderHandler {

    private companion object {
        const val TAG = "ElapsedReminderHandler"
        const val FN_HANDLE = "handleIfElapsed"
    }

    override suspend fun handleIfElapsed(
        reminder: EventReminder,
        nowEpochMillis: Long
    ): Boolean {

        Timber.tag(TAG).d("ELAPSED | ElapsedReminderHandler::%s - Evaluating id=%s enabled=%s deleted=%s repeatRule=%s eventAt=%d now=%d", FN_HANDLE, reminder.id, reminder.enabled, reminder.isDeleted, reminder.repeatRule, reminder.eventEpochMillis, nowEpochMillis)

        // --- guard: deleted ---
        if (reminder.isDeleted) {
            Timber.tag(TAG).d("ELAPSED | ElapsedReminderHandler::%s - Skip (deleted) id=%s", FN_HANDLE, reminder.id)
            return false
        }

        // --- guard: already disabled ---
        if (!reminder.enabled) {
            Timber.tag(TAG).d("ELAPSED | ElapsedReminderHandler::%s - Skip (already disabled) id=%s", FN_HANDLE, reminder.id)
            return false
        }

        // --- guard: repeating reminder ---
        if (reminder.repeatRule != null) {
            Timber.tag(TAG).d("ELAPSED | ElapsedReminderHandler::%s - Skip (repeating rule=%s) id=%s", FN_HANDLE, reminder.repeatRule, reminder.id)
            return false
        }

        // --- guard: not elapsed yet ---
        if (reminder.eventEpochMillis >= nowEpochMillis) {
            Timber.tag(TAG).d("ELAPSED | ElapsedReminderHandler::%s - Skip (not elapsed) id=%s eventAt=%d", FN_HANDLE, reminder.id, reminder.eventEpochMillis)
            return false
        }

        // --- action: disable ---
        Timber.tag(TAG).i("ELAPSED | ElapsedReminderHandler::%s - Disabling one-time reminder id=%s eventAt=%d now=%d", FN_HANDLE, reminder.id, reminder.eventEpochMillis, nowEpochMillis)

        reminderRepository.updateEnabled(
            id = reminder.id,
            enabled = false,
            isDeleted = false,
            updatedAt = nowEpochMillis
        )

        Timber.tag(TAG).i("ELAPSED | ElapsedReminderHandler::%s - Disabled successfully id=%s", FN_HANDLE, reminder.id)
        Timber.tag(DELETE_TAG).i("ELAPSED | ElapsedReminderHandler::%s - Disabled successfully id=%s", FN_HANDLE, reminder.id)

        return true
    }
}
