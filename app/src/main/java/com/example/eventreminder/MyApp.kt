package com.example.eventreminder

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.eventreminder.data.local.DatabaseSeeder
import com.example.eventreminder.workers.AutoDismissCleanupWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
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
        // Auto-dismiss cleanup worker (GLOBAL, APP-LEVEL)
        // ------------------------------------------------------------
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                AUTO_DISMISS_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // ifperiod wants to change run one time with REPLACE than changed back to KEEP
                PeriodicWorkRequestBuilder<AutoDismissCleanupWorker>(
                    24,
                    TimeUnit.HOURS
                ).build()
            )

        Timber.d("AUTO_DISMISS periodic worker ensured [MyApp.kt::onCreate]")

        // 🚀 Seed the database (runs ONLY if DB is empty)
        // databaseSeeder.seedIfEmpty()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private companion object {
        private const val AUTO_DISMISS_WORK_NAME = "auto_dismiss_cleanup"
    }
}
