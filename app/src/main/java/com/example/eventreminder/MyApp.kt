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
import com.example.eventreminder.logging.RESTORE_NOT_DISMISSED_TAG
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
    private val NEXT_7_DAYS_HOUR = 7
    private val NEXT_7_DAYS_MINUTE = 10

    private val AUTO_DISMISS_HOUR = 7
    private val AUTO_DISMISS_MINUTE = 10

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // ------------------------------------------------------------
        // 🔁 Restore fired-but-not-dismissed notifications (UI only)
        // ------------------------------------------------------------
        Timber.tag(RESTORE_NOT_DISMISSED_TAG).i("RESTORE_INITIATED [MyApp.kt::onCreate]")

        CoroutineScope(Dispatchers.Default).launch {
            try {
                NotificationRestoreManager.restoreActiveNotifications(this@MyApp)
                Timber.tag(RESTORE_NOT_DISMISSED_TAG).i("RESTORE_JOB_SUCCESS [MyApp.kt::onCreate]")
            } catch (t: Throwable) {
                Timber.e(t, "RESTORE_FAILED [MyApp.kt::onCreate]")
            } finally {
                Timber.tag(RESTORE_NOT_DISMISSED_TAG).i("RESTORE_JOB_COMPLETED [MyApp.kt::onCreate]")
            }
        }

        val prefs = getSharedPreferences(PREF_WM_FLAGS, Context.MODE_PRIVATE)

        // ============================================================
        // 🔁 AUTO-DISMISS WORKER
        // ============================================================
        val autoDismissTimeKey = buildTimeKey(hour = AUTO_DISMISS_HOUR, minute = AUTO_DISMISS_MINUTE)
        val storedAutoDismissKey = prefs.getString(KEY_AUTO_DISMISS_TIME, null)
        val shouldRescheduleAutoDismiss = storedAutoDismissKey != autoDismissTimeKey

        if (shouldRescheduleAutoDismiss) {
            val initialDelayMillis = computeInitialDelay(AUTO_DISMISS_HOUR, AUTO_DISMISS_MINUTE)
            val nextRun = computeNextRunTime(AUTO_DISMISS_HOUR, AUTO_DISMISS_MINUTE)

            WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                    AUTO_DISMISS_WORK_NAME,
                    if (BuildConfig.DEBUG) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<AutoDismissCleanupWorker>(24, TimeUnit.HOURS)
                        .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                        .build()
                )

            prefs.edit()
                .putString(KEY_AUTO_DISMISS_TIME, autoDismissTimeKey)
                .apply()

            Timber.tag(DISMISS_TAG).i("AUTO_DISMISS worker RESCHEDULED time=$autoDismissTimeKey " + "target=${nextRun.time} delay=${initialDelayMillis}ms " + "[MyApp.kt::onCreate]")
        } else {
            Timber.tag(DISMISS_TAG).d("AUTO_DISMISS worker unchanged (time=$autoDismissTimeKey) " + "[MyApp.kt::onCreate]")
        }

        // ============================================================
        // 📄 NEXT 7 DAYS PDF WORKER
        // ============================================================
        val next7TimeKey = buildTimeKey(NEXT_7_DAYS_HOUR, NEXT_7_DAYS_MINUTE)
        val storedNext7Key = prefs.getString(KEY_NEXT7_TIME, null)
        val shouldRescheduleNext7 = storedNext7Key != next7TimeKey

        if (shouldRescheduleNext7) {
            val initialDelayMillis = computeInitialDelay(NEXT_7_DAYS_HOUR, NEXT_7_DAYS_MINUTE)
            val nextRun = computeNextRunTime(NEXT_7_DAYS_HOUR, NEXT_7_DAYS_MINUTE)

            WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                    NEXT_7_DAYS_PDF_WORK_NAME,
                    if (BuildConfig.DEBUG) ExistingPeriodicWorkPolicy.REPLACE else ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<Next7DaysPdfWorker>(24, TimeUnit.HOURS)
                        .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                        .build()
                )

            prefs.edit()
                .putString(KEY_NEXT7_TIME, next7TimeKey)
                .apply()

            Timber.tag(SHARE_PDF_TAG).i("NEXT_7_DAYS_PDF worker RESCHEDULED time=$next7TimeKey " + "target=${nextRun.time} delay=${initialDelayMillis}ms " + "[MyApp.kt::onCreate]")
        } else {
            Timber.tag(SHARE_PDF_TAG).d("NEXT_7_DAYS_PDF worker unchanged (time=$next7TimeKey) " + "[MyApp.kt::onCreate]")
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    // ============================================================
    // ⏱ Time helpers
    // ============================================================
    private fun buildTimeKey(hour: Int, minute: Int): String =
        "%02d:%02d".format(hour, minute)

    private fun computeInitialDelay(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val nextRun = computeNextRunTime(hour, minute)
        return nextRun.timeInMillis - now.timeInMillis
    }

    private fun computeNextRunTime(hour: Int, minute: Int): Calendar {
        val now = Calendar.getInstance()

        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (before(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
    }

    private companion object {
        private const val PREF_WM_FLAGS = "wm_flags"
        private const val AUTO_DISMISS_WORK_NAME = "auto_dismiss_cleanup"
        private const val NEXT_7_DAYS_PDF_WORK_NAME = "next_7_days_pdf_whatsapp"
        private const val KEY_NEXT7_TIME = "next7days_time"
        private const val KEY_AUTO_DISMISS_TIME = "auto_dismiss_time"
    }
}
