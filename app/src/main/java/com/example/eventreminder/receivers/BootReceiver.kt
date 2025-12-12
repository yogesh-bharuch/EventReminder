package com.example.eventreminder.receivers

// =============================================================
// BootReceiver — Clean Reboot Restore (Engine-Driven)
// UUID-only version using ReminderSchedulingEngine.
//
// Responsibilities AFTER engine adoption:
//  • Detect boot/package-replaced event
//  • Load reminders from repository
//  • Delegate ALL scheduling/missed-fire logic to Engine
//
// ZERO alarm logic lives here now.
// =============================================================

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.scheduler.ReminderSchedulingEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

private const val TAG = "BootReceiver"

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var repo: ReminderRepository
    @Inject lateinit var schedulingEngine: ReminderSchedulingEngine

    override fun onReceive(context: Context, intent: Intent) {

        val isBootEvent =
            intent.action == Intent.ACTION_BOOT_COMPLETED ||
                    intent.action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!isBootEvent) return

        Timber.tag(TAG).i("BOOT_COMPLETED → Delegating boot restore to SchedulingEngine…")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminders = repo.getNonDeletedEnabled()
                val now = Instant.now().toEpochMilli()

                reminders.forEach { reminder ->
                    try {
                        schedulingEngine.processBootRestore(
                            reminder = reminder,
                            nowEpochMillis = now
                        )
                    } catch (t: Throwable) {
                        Timber.tag(TAG).e(
                            t,
                            "Engine boot-restore failed for ${reminder.id}"
                        )
                    }
                }

                Timber.tag(TAG).i("BOOT restore complete ✔")

            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "BOOT restore FAILED ❌")
            }
        }
    }
}
