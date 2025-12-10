package com.example.eventreminder

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.eventreminder.data.local.DatabaseSeeder
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

// 🧩 Root of your Hilt dependency graph
@HiltAndroidApp
class MyApp : Application(), Configuration.Provider {

    // 👇 Hilt automatically injects this WorkerFactory
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // 👇 Inject our new DB seeder
    @Inject
    lateinit var databaseSeeder: DatabaseSeeder

    override fun onCreate() {
        super.onCreate()

        // FORCE DB RESET (TEMPORARILY DURING DEVELOPMENT)
        deleteDatabase("event_reminder_db")

        // 🌲 Initialize Timber for debug logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber initialized in DEBUG mode")
        }

        // 🚀 Seed the database (runs ONLY if DB is empty)
        //databaseSeeder.seedIfEmpty()
    }

    // ✅ Must override this property, not a function
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory) // integrates Hilt with WorkManager
            .build()
}
