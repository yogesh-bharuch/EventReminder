package com.example.eventreminder

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.eventreminder.data.local.DatabaseSeeder
import com.example.eventreminder.logging.DISMISS_TAG
import com.example.eventreminder.logging.SHARE_PDF_TAG
import com.example.eventreminder.notifications.NotificationRestoreManager
import com.example.eventreminder.workers.AutoDismissCleanupWorker
import com.example.eventreminder.workers.Next7DaysPdfWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MyApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var databaseSeeder: DatabaseSeeder

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber initialized in DEBUG mode [MyApp.kt::onCreate]")
        }

        // ------------------------------------------------------------
        // 🔁 Restore fired-but-not-dismissed notifications (UI only)
        // ------------------------------------------------------------
        CoroutineScope(Dispatchers.Default).launch {
            try {
                NotificationRestoreManager.restoreActiveNotifications(this@MyApp)
            } catch (t: Throwable) {
                Timber.e(t, "RESTORE_FAILED [MyApp.kt::onCreate]")
            }
        }

        // ------------------------------------------------------------
        // 🔁 Auto-dismiss cleanup worker (GLOBAL, APP-LEVEL) — UNCHANGED
        // ------------------------------------------------------------
        val initialDelayMillis = computeInitialDelay()
        val policy = if (BuildConfig.DEBUG) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                AUTO_DISMISS_WORK_NAME,
                policy,
                PeriodicWorkRequestBuilder<AutoDismissCleanupWorker>(
                    24,
                    TimeUnit.HOURS
                )
                    .setInitialDelay(
                        initialDelayMillis,
                        TimeUnit.MILLISECONDS
                    )
                    .build()
            )

        Timber.tag(DISMISS_TAG).i("AUTO_DISMISS periodic worker ensured [MyApp.kt::onCreate]")

        // ------------------------------------------------------------
        // 📄 Next 7 Days Reminders PDF → Notification (DAILY @ 4:00 PM)
        // ------------------------------------------------------------

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                NEXT_7_DAYS_PDF_WORK_NAME,
                policy,
                PeriodicWorkRequestBuilder<Next7DaysPdfWorker>(
                    24,
                    TimeUnit.HOURS
                )
                    .setInitialDelay(
                        initialDelayMillis,
                        TimeUnit.MILLISECONDS
                    )
                    .build()
            )

        Timber.tag(SHARE_PDF_TAG).d("NEXT_7_DAYS_PDF worker scheduled delay=${initialDelayMillis}ms [MyApp.kt::onCreate]")


        // ------------------------------------------------------------
        // 🚀 DEV ONLY (unchanged)
        // ------------------------------------------------------------
        // databaseSeeder.seedIfEmpty()
        // deleteDatabase("event_reminder_db")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Computes delay until next **4:00 PM** local time.
     *
     * CHANGE:
     * - Previously targeted 12:30 PM
     * - Now targets 4:00 PM
     *
     * WorkManager constraint:
     * - Best-effort timing (not exact alarm)
     */
    private fun computeInitialDelay(): Long {
        val now = Calendar.getInstance()

        val next4pm = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (before(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val delay = next4pm.timeInMillis - now.timeInMillis

        Timber.tag(SHARE_PDF_TAG).d("InitialDelay computed delay=${delay}ms target=${next4pm.time} [MyApp.kt::computeInitialDelayFor4PM]")

        return delay
    }

    private companion object {
        private const val AUTO_DISMISS_WORK_NAME = "auto_dismiss_cleanup"
        private const val NEXT_7_DAYS_PDF_WORK_NAME = "next_7_days_pdf_whatsapp"
    }
}
