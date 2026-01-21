package com.example.eventreminder.workers

import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.eventreminder.data.local.ReminderFireStateDao
import com.example.eventreminder.data.repo.ReminderRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import kotlin.math.abs
import com.example.eventreminder.logging.DISMISS_TAG

private const val AUTO_DISMISS_THRESHOLD_MILLIS =
    48L * 60 * 60 * 1000 // 48h

/**
 * AutoDismissCleanupWorker
 *
 * Called by:
 * - WorkManager (periodic)
 *
 * Responsibility:
 * - Find stale fire-state rows
 * - Cancel visible notifications
 * - Record dismissal exactly like manual dismiss
 *
 * Return:
 * - Result.success() always (idempotent cleanup)
 */
class AutoDismissCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun reminderRepository(): ReminderRepository
        fun fireStateDao(): ReminderFireStateDao
    }

    override suspend fun doWork(): Result {

        Timber.tag(DISMISS_TAG).i(
            "AUTO_DISMISS_WORKER_START [AutoDismissCleanupWorker.kt::doWork]"
        )

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )

        val repo = entryPoint.reminderRepository()
        val fireStateDao = entryPoint.fireStateDao()
        val nm = applicationContext.getSystemService(NotificationManager::class.java)

        val cutoff = System.currentTimeMillis() - AUTO_DISMISS_THRESHOLD_MILLIS

        val staleRows = fireStateDao.getStaleFireStates(cutoff)

        Timber.tag(DISMISS_TAG).i("AUTO_DISMISS_FOUND count=${staleRows.size} cutoff=$cutoff [AutoDismissCleanupWorker.kt::doWork]")

        for (state in staleRows) {

            val reminderId = state.reminderId
            val offsetMillis = 0L // FINAL OFFSET ONLY (by design)

            val notificationId =
                generateNotificationIdFromString(reminderId, offsetMillis)

            Timber.tag(DISMISS_TAG).i("AUTO_DISMISS_APPLY → id=$reminderId notifId=$notificationId [AutoDismissCleanupWorker.kt::doWork]")

            try {
                nm.cancel(notificationId)

                repo.recordDismissed(
                    reminderId = reminderId,
                    offsetMillis = offsetMillis
                )

            } catch (t: Throwable) {
                Timber.tag(DISMISS_TAG).e(t, "AUTO_DISMISS_FAILED → id=$reminderId [AutoDismissCleanupWorker.kt::doWork]")
            }
        }

        Timber.tag(DISMISS_TAG).i("AUTO_DISMISS_WORKER_DONE [AutoDismissCleanupWorker.kt::doWork]")

        return Result.success()
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
