package com.example.eventreminder.notifications

import android.content.Context
import com.example.eventreminder.data.local.ReminderFireStateDao
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.receivers.ReminderReceiver
import com.example.eventreminder.util.NotificationHelper
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs

private const val TAG = "NotificationRestore"

/**
 * NotificationRestoreManager
 *
 * Called by:
 * - App startup (MyApp)
 * - BootReceiver (future)
 *
 * Responsibility:
 * - Restore notification UI for already-fired reminders
 *
 * Rules:
 * - lastFiredAt != null
 * - dismissedAt == null
 * - NO scheduling
 * - NO DB writes
 * - Idempotent
 */
object NotificationRestoreManager {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RestoreEntryPoint {
        fun reminderRepository(): ReminderRepository
        fun fireStateDao(): ReminderFireStateDao
    }

    /**
     * Restore active fired notifications into tray.
     *
     * Caller:
     * - Application.onCreate
     *
     * Return:
     * - Unit
     */
    suspend fun restoreActiveNotifications(context: Context) {
        withContext(Dispatchers.IO) {

            Timber.tag(TAG).i("RESTORE_START [NotificationRestoreManager.kt::restoreActiveNotifications]")

            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                RestoreEntryPoint::class.java
            )

            val repo = entryPoint.reminderRepository()
            val fireStateDao = entryPoint.fireStateDao()

            val fireStates = fireStateDao.getActiveFiredFireStates()

            Timber.tag(TAG).i("RESTORE_FOUND count=${fireStates.size} [NotificationRestoreManager.kt::restoreActiveNotifications]")

            for (state in fireStates) {

                val reminder = repo.getReminder(state.reminderId)
                if (reminder == null || reminder.isDeleted || !reminder.enabled) {
                    Timber.tag(TAG).w("RESTORE_SKIP invalid reminder id=${state.reminderId} " + "[NotificationRestoreManager.kt::restoreActiveNotifications]")
                    continue
                }

                val offsetMillis = state.offsetMillis
                val notificationId =
                    generateNotificationIdFromString(reminder.id, offsetMillis)

                Timber.tag(TAG).i("RESTORE_APPLY → id=${reminder.id} notifId=$notificationId " + "[NotificationRestoreManager.kt::restoreActiveNotifications]")

                NotificationHelper.showNotification(
                    context = context,
                    notificationId = notificationId,
                    title = reminder.title,
                    message = reminder.description ?: "",
                    eventType = "", // inferred again in NotificationHelper if needed
                    extras = mapOf(
                        ReminderReceiver.EXTRA_FROM_NOTIFICATION to true,
                        ReminderReceiver.EXTRA_REMINDER_ID_STRING to reminder.id,
                        ReminderReceiver.EXTRA_OFFSET_MILLIS to offsetMillis
                    ),
                    silent = true
                )
            }

            Timber.tag(TAG).i("RESTORE_DONE [NotificationRestoreManager.kt::restoreActiveNotifications]")
        }
    }

    /**
     * Same notificationId derivation as ReminderReceiver
     */
    private fun generateNotificationIdFromString(
        idString: String,
        offsetMillis: Long
    ): Int {
        val raw = idString.hashCode() xor offsetMillis.hashCode()
        return if (raw == Int.MIN_VALUE) Int.MAX_VALUE else abs(raw)
    }
}
