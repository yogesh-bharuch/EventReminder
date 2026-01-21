package com.example.eventreminder

import android.app.Application
import android.content.Context
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

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var databaseSeeder: DatabaseSeeder

    // ============================================================
    // 🔧 CONFIG — CHANGE TIME ONLY HERE
    // ============================================================
    private val NEXT_7_DAYS_HOUR = 8
    private val NEXT_7_DAYS_MINUTE = 50

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
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

        val initialDelayMillis = computeInitialDelay()
        val nextRun = computeNextRunTime()
        val timeKey = buildTimeKey()

        // ------------------------------------------------------------
        // 🔁 Auto-dismiss cleanup worker (SAFE TO ALWAYS RESCHEDULE)
        // ------------------------------------------------------------
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                AUTO_DISMISS_WORK_NAME,
                if (BuildConfig.DEBUG) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AutoDismissCleanupWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                    .build()
            )

        Timber.tag(DISMISS_TAG).i("AUTO_DISMISS scheduled delay=${initialDelayMillis}ms [MyApp.kt::onCreate]")

        // ------------------------------------------------------------
        // 📄 Next 7 Days PDF — RESCHEDULE ON TIME CHANGE
        // ------------------------------------------------------------
        val prefs = getSharedPreferences("wm_flags", Context.MODE_PRIVATE)
        val storedTimeKey = prefs.getString(KEY_NEXT7_TIME, null)

        val shouldReschedule = storedTimeKey != timeKey

        if (shouldReschedule) {

            WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                    NEXT_7_DAYS_PDF_WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE, // 🔑 IMPORTANT
                    PeriodicWorkRequestBuilder<Next7DaysPdfWorker>(24, TimeUnit.HOURS)
                        .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                        .build()
                )

            prefs.edit().putString(KEY_NEXT7_TIME, timeKey).apply()

            Timber.tag(SHARE_PDF_TAG).i("NEXT_7_DAYS_PDF worker RESCHEDULED time=$timeKey " + "target=${nextRun.time} delay=${initialDelayMillis}ms " + "[MyApp.kt::onCreate]")
        } else {
            Timber.tag(SHARE_PDF_TAG).d("NEXT_7_DAYS_PDF worker unchanged (time=$timeKey) [MyApp.kt::onCreate]")
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // ============================================================
    // ⏱ Time helpers
    // ============================================================
    private fun buildTimeKey(): String =
        "%02d:%02d".format(NEXT_7_DAYS_HOUR, NEXT_7_DAYS_MINUTE)

    private fun computeInitialDelay(): Long {
        val now = Calendar.getInstance()
        val nextRun = computeNextRunTime()
        return nextRun.timeInMillis - now.timeInMillis
    }

    private fun computeNextRunTime(): Calendar {
        val now = Calendar.getInstance()

        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, NEXT_7_DAYS_HOUR)
            set(Calendar.MINUTE, NEXT_7_DAYS_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (before(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }

    private companion object {
        private const val AUTO_DISMISS_WORK_NAME = "auto_dismiss_cleanup"
        private const val NEXT_7_DAYS_PDF_WORK_NAME = "next_7_days_pdf_whatsapp"
        private const val KEY_NEXT7_TIME = "next7days_time"
    }
}
