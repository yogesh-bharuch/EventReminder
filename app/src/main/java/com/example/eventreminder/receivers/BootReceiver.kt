package com.example.eventreminder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.logging.BOOT_RECEIVER_TAG
import com.example.eventreminder.logging.RESTORE_NOT_DISMISSED_TAG
import com.example.eventreminder.notifications.NotificationRestoreManager
import com.example.eventreminder.scheduler.ReminderSchedulingEngine
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
    @Inject lateinit var schedulingEngine: ReminderSchedulingEngine

    /**
     * Caller(s):
     *  - Android system (BOOT_COMPLETED)
     *  - Android system (MY_PACKAGE_REPLACED)
     *
     * Responsibility:
     *  - Detect device reboot or app replacement
     *  - Restore scheduling state via ReminderSchedulingEngine
     *  - Restore fired-but-not-dismissed notifications (UI only)
     *
     * Return:
     *  - Unit
     */
    override fun onReceive(context: Context, intent: Intent) {

        val isBootEvent =
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
                    intent.action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!isBootEvent) return

        Timber.tag(BOOT_RECEIVER_TAG).i("BOOT_EVENT action=${intent.action} [BootReceiver.kt::onReceive]")

        CoroutineScope(Dispatchers.IO).launch {

            // =====================================================
            // 1️⃣ Engine boot restore (scheduling + missed detection)
            // =====================================================
            try {
                val reminders = repo.getNonDeletedEnabled()
                val nowEpoch = Instant.now().toEpochMilli()

                reminders.forEach { reminder ->
                    try {
                        schedulingEngine.processBootRestore(
                            reminder = reminder,
                            nowEpochMillis = nowEpoch
                        )
                    } catch (t: Throwable) {
                        Timber.tag(BOOT_RECEIVER_TAG).e(t, "ENGINE_RESTORE_FAILED id=${reminder.id} [BootReceiver.kt::onReceive]")
                    }
                }

                Timber.tag(BOOT_RECEIVER_TAG).i("BOOT_ENGINE_RESTORE_SUCCESS count=${reminders.size} [BootReceiver.kt::onReceive]")

            } catch (t: Throwable) {
                Timber.tag(BOOT_RECEIVER_TAG).e(t, "BOOT_ENGINE_RESTORE_FATAL [BootReceiver.kt::onReceive]")
            }

            // =====================================================
            // 2️⃣ Restore fired-but-not-dismissed notifications (UI)
            // =====================================================
            Timber.tag(RESTORE_NOT_DISMISSED_TAG).i("RESTORE_INITIATED source=boot [BootReceiver.kt::onReceive]")

            try {
                NotificationRestoreManager.restoreActiveNotifications(context)

                Timber.tag(RESTORE_NOT_DISMISSED_TAG).i("RESTORE_JOB_SUCCESS source=boot [BootReceiver.kt::onReceive]")
            } catch (t: Throwable) {
                Timber.tag(RESTORE_NOT_DISMISSED_TAG).e(t, "RESTORE_FAILED source=boot [BootReceiver.kt::onReceive]")
            } finally {
                Timber.tag(RESTORE_NOT_DISMISSED_TAG).i("RESTORE_JOB_COMPLETED source=boot [BootReceiver.kt::onReceive]")
            }
        }
    }
}
