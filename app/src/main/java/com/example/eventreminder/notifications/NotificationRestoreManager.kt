package com.example.eventreminder.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.eventreminder.data.local.ReminderFireStateDao
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.data.session.SessionRepository
import com.example.eventreminder.logging.RESTORE_NOT_DISMISSED_TAG
import com.example.eventreminder.receivers.ReminderReceiver
import com.example.eventreminder.util.NotificationHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * NotificationRestoreManager
 *
 * Called by:
 * - Application.onCreate (cold start)
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
 *
 * AUTH RULE:
 * - SessionRepository is the ONLY authority
 */
object NotificationRestoreManager {

    private const val SILENT_RESTORE_CHANNEL_ID = "restore_silent"

    // ---------------------------------------------------------
    // Hilt EntryPoint
    // ---------------------------------------------------------
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RestoreEntryPoint {
        fun reminderRepository(): ReminderRepository
        fun fireStateDao(): ReminderFireStateDao
        fun sessionRepository(): SessionRepository
    }

    /**
     * Restore active fired notifications into tray.
     */
    suspend fun restoreActiveNotifications(context: Context) {
        withContext(Dispatchers.IO) {

            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                RestoreEntryPoint::class.java
            )

            // -------------------------------------------------
            // 🔐 Session guard (AUTHORITATIVE)
            // -------------------------------------------------
            val session = entryPoint
                .sessionRepository()
                .sessionState
                .first()

            if (session.uid == null) {
                val nowEpoch = System.currentTimeMillis()
                val readable = DateTimeFormatter
                    .ofPattern("dd MMM, yyyy HH:mm:ss 'GMT'XXX")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(nowEpoch))

                Timber.tag(RESTORE_NOT_DISMISSED_TAG).w("RESTORE_SKIPPED no_session time=$readable epoch=$nowEpoch [NotificationRestoreManager.kt::restoreActiveNotifications]")
                return@withContext
            }

            Timber.tag(RESTORE_NOT_DISMISSED_TAG).i("RESTORE_START uid=${session.uid} [NotificationRestoreManager.kt::restoreActiveNotifications]")

            val repo = entryPoint.reminderRepository()
            val fireStateDao = entryPoint.fireStateDao()

            val fireStates = fireStateDao.getActiveFiredFireStates()

            Timber.tag(RESTORE_NOT_DISMISSED_TAG).i("RESTORE_FOUND count=${fireStates.size} [NotificationRestoreManager.kt::restoreActiveNotifications]")

            ensureSilentRestoreChannel(context)

            for (state in fireStates) {

                val reminder = repo.getReminder(state.reminderId)
                if (reminder == null || reminder.isDeleted || !reminder.enabled) {
                    Timber.tag(RESTORE_NOT_DISMISSED_TAG).w("RESTORE_SKIP invalid reminder id=${state.reminderId} [NotificationRestoreManager.kt::restoreActiveNotifications]")
                    continue
                }

                val notificationId = generateNotificationIdFromString(
                    idString = reminder.id,
                    offsetMillis = state.offsetMillis
                )

                Timber.tag(RESTORE_NOT_DISMISSED_TAG).i("RESTORE_APPLY id=${reminder.id} notifId=$notificationId [NotificationRestoreManager.kt::restoreActiveNotifications]")

                NotificationHelper.showNotification(
                    context = context,
                    notificationId = notificationId,
                    title = reminder.title,
                    message = reminder.description ?: "",
                    eventType = "",
                    extras = mapOf(
                        ReminderReceiver.EXTRA_FROM_NOTIFICATION to true,
                        ReminderReceiver.EXTRA_REMINDER_ID_STRING to reminder.id,
                        ReminderReceiver.EXTRA_OFFSET_MILLIS to state.offsetMillis
                    ),
                    silent = true, // semantic only — channel enforces silence


                )
            }

            Timber.tag(RESTORE_NOT_DISMISSED_TAG).i("RESTORE_DONE [NotificationRestoreManager.kt::restoreActiveNotifications]")
        }
    }

    // ---------------------------------------------------------
    // 🔕 Silent restore channel (system-enforced silence)
    // ---------------------------------------------------------
    private fun ensureSilentRestoreChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(NotificationManager::class.java)

        if (nm.getNotificationChannel(SILENT_RESTORE_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            SILENT_RESTORE_CHANNEL_ID,
            "Restored Notifications (Silent)",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Restored fired reminders without alert"
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }

        nm.createNotificationChannel(channel)

        Timber.tag(RESTORE_NOT_DISMISSED_TAG).i("RESTORE_CHANNEL_CREATED id=$SILENT_RESTORE_CHANNEL_ID [NotificationRestoreManager.kt::ensureSilentRestoreChannel]")
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
