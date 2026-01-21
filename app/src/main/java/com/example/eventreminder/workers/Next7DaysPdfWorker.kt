package com.example.eventreminder.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.eventreminder.R
import com.example.eventreminder.data.session.SessionRepository
import com.example.eventreminder.logging.SHARE_PDF_TAG
import com.example.eventreminder.pdf.Next7DaysPdfUseCase
import com.example.eventreminder.receivers.ReminderReceiver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val NOTIFICATION_ID = 7001
private const val CHANNEL_ID = "next_7_days_pdf"

/**
 * Shared time context for this Worker.
 */
private val WORKER_ZONE_ID: ZoneId = ZoneId.systemDefault()

private val WORKER_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter
        .ofPattern("dd MMM, yyyy HH:mm:ss 'GMT'XXX")
        .withZone(WORKER_ZONE_ID)

/**
 * Next7DaysPdfWorker
 *
 * Caller:
 *  - WorkManager (periodic, daily)
 *
 * Responsibility:
 *  - Guard execution using SessionRepository
 *  - Delegate PDF generation to Next7DaysPdfUseCase
 *  - Show notification with Share + Dismiss actions
 *
 * IMPORTANT:
 *  - ZERO PDF logic lives here
 *  - ZERO reminder grouping logic lives here
 */
class Next7DaysPdfWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    // ---------------------------------------------------------
    // EntryPoint (NO HiltWorker)
    // ---------------------------------------------------------
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun next7DaysPdfUseCase(): Next7DaysPdfUseCase
        fun sessionRepository(): SessionRepository
    }

    override suspend fun doWork(): Result {

        val startEpoch = System.currentTimeMillis()

        Timber.tag(SHARE_PDF_TAG).i("WORK_START time=${WORKER_TIME_FORMATTER.format(Instant.ofEpochMilli(startEpoch))} " + "epoch=$startEpoch [Next7DaysPdfWorker.kt::doWork]")

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )

        // -------------------------------------------------
        // 🔐 SESSION GUARD (AUTHORITATIVE)
        // -------------------------------------------------
        val session = entryPoint
            .sessionRepository()
            .sessionState
            .first()

        if (session.uid == null) {
            Timber.tag(SHARE_PDF_TAG).w("WORK_SKIPPED no_session time=${WORKER_TIME_FORMATTER.format(Instant.now())} " + "[Next7DaysPdfWorker.kt::doWork]")
            return Result.success()
        }

        // -------------------------------------------------
        // 🚀 Delegate to UseCase
        // -------------------------------------------------
        return try {

            val uri: Uri? = entryPoint
                .next7DaysPdfUseCase()
                .generate()

            if (uri == null) {
                Timber.tag(SHARE_PDF_TAG).e("PDF_GENERATION_FAILED [Next7DaysPdfWorker.kt::doWork]")
                return Result.success()
            }

            Timber.tag(SHARE_PDF_TAG).i("PDF_GENERATED uri=$uri [Next7DaysPdfWorker.kt::doWork]")

            showNotification(uri)

            Timber.tag(SHARE_PDF_TAG).i("WORK_COMPLETE_SUCCESS [Next7DaysPdfWorker.kt::doWork]")

            Result.success()

        } catch (t: Throwable) {
            Timber.tag(SHARE_PDF_TAG).e(t, "WORK_FAILED_EXCEPTION [Next7DaysPdfWorker.kt::doWork]")
            Result.success()
        }
    }

    // =========================================================
    // Notification (Share + Dismiss)
    // =========================================================
    private fun showNotification(uri: Uri) {

        val nm =
            applicationContext.getSystemService(NotificationManager::class.java)

        ensureNotificationChannel(nm)

        // -----------------------------------------------------
        // Share PDF
        // -----------------------------------------------------
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val sharePI = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            Intent.createChooser(shareIntent, "Share PDF"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // -----------------------------------------------------
        // Dismiss → ReminderReceiver
        // -----------------------------------------------------
        val dismissIntent = Intent(applicationContext, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_DISMISS
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
        }

        val dismissPI = PendingIntent.getBroadcast(
            applicationContext,
            NOTIFICATION_ID + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📄 Next 7 Days Reminders")
            .setContentText("Your reminders report is ready")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .addAction(R.drawable.ic_open, "Share PDF", sharePI)
            .addAction(R.drawable.ic_close, "Dismiss", dismissPI)
            .build()

        nm.notify(NOTIFICATION_ID, notification)

        Timber.tag(SHARE_PDF_TAG).d("NOTIFICATION_SHOWN notifId=$NOTIFICATION_ID [Next7DaysPdfWorker.kt::showNotification]")
    }

    // =========================================================
    // Notification Channel
    // =========================================================
    private fun ensureNotificationChannel(nm: NotificationManager) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Next 7 Days PDF",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Daily next 7 days reminders PDF"
            enableVibration(true)
        }

        nm.createNotificationChannel(channel)
    }
}
