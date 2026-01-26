package com.example.eventreminder.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.eventreminder.sync.core.SyncEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Background worker that runs sync periodically.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            syncEngine.syncAll()
            Result.success()
        } catch (t: Throwable) {
            Timber.e(t, "SyncWorker failed")
            Result.retry()
        }
    }
}

/*
* ✅ 10. SyncWorker.kt
A WorkManager background worker that periodically runs:
syncEngine.syncAll()
👉 Allows automatic sync in background even when app not opened
* */
