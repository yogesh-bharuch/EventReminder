package com.example.eventreminder.workers

// =============================================================
// Next7DaysPdfWorker — Daily PDF → Notification (Share + Dismiss)
// =============================================================

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
import com.example.eventreminder.logging.SHARE_PDF_TAG
import com.example.eventreminder.pdf.PdfCell
import com.example.eventreminder.pdf.PdfLayoutConfig
import com.example.eventreminder.pdf.PdfRepository
import com.example.eventreminder.pdf.ReminderReportDataBuilder
import com.example.eventreminder.receivers.ReminderReceiver
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val NOTIFICATION_ID = 7001
private const val CHANNEL_ID = "next_7_days_pdf"

/**
 * Shared time context for this Worker.
 * Single source of truth for readable + epoch logs.
 */
private val WORKER_ZONE_ID: ZoneId = ZoneId.systemDefault()

private val WORKER_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter
        .ofPattern("dd MMM, yyyy HH:mm:ss 'GMT'XXX")
        .withZone(WORKER_ZONE_ID)

/**
 * Next7DaysPdfWorker
 *
 * Called by:
 *  - WorkManager (periodic, daily)
 *
 * Responsibility:
 *  - Generate "Next 7 Days Reminders" PDF (headless).
 *  - Show notification with:
 *      • Share PDF
 *      • Dismiss (REUSE ReminderReceiver)
 *
 * Constraints:
 *  - NO UI launch
 *  - NO ViewModel
 *  - NO HiltWorker
 *  - EntryPointAccessors only
 *
 * Return:
 *  - Result.success() always (idempotent)
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
        fun reminderReportDataBuilder(): ReminderReportDataBuilder
        fun pdfRepository(): PdfRepository
    }

    /**
     * Caller:
     *  - WorkManager
     *
     * Responsibility:
     *  - Executes daily background PDF generation.
     *  - Logs lifecycle with readable time + epoch.
     *  - Never throws (idempotent worker).
     *
     * Return:
     *  - Result.success()
     */
    override suspend fun doWork(): Result {

        val startEpoch = System.currentTimeMillis()

        Timber.tag(SHARE_PDF_TAG).i("WORK_START time=${WORKER_TIME_FORMATTER.format(Instant.ofEpochMilli(startEpoch))} epoch=$startEpoch " + "[Next7DaysPdfWorker.kt::doWork]")

        // -------------------------------------------------
        // 🔐 Auth guard — REQUIRED for background execution
        // -------------------------------------------------
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Timber.tag(SHARE_PDF_TAG).w(
                "WORK_SKIPPED user=logged_out time=${WORKER_TIME_FORMATTER.format(Instant.now())} epoch=${System.currentTimeMillis()} " +
                        "[Next7DaysPdfWorker.kt::doWork]"
            )
            return Result.success()
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )

        val builder = entryPoint.reminderReportDataBuilder()
        val repository = entryPoint.pdfRepository()

        try {
            // -------------------------------------------------
            // 1️⃣ Load reminder data
            // -------------------------------------------------
            val reminders = builder.buildNext7DaysReminders()

            Timber.tag(SHARE_PDF_TAG).d("DATA_LOADED count=${reminders.size} time=${WORKER_TIME_FORMATTER.format(Instant.now())} " + "[Next7DaysPdfWorker.kt::doWork]")

            // Timber.tag(SHARE_PDF_TAG).d( "ROWS_BUILD_START time=${WORKER_TIME_FORMATTER.format(Instant.now())} " + "[Next7DaysPdfWorker.kt::doWork]" )

            // -------------------------------------------------
            // 2️⃣ Build PDF rows (readable time + offset)
            // -------------------------------------------------
            val rowTimeFormatter =
                DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(WORKER_ZONE_ID)

            val rows = reminders.map { alarm ->
                listOf(
                    PdfCell.TextCell(alarm.description ?: "-"),
                    PdfCell.TextCell(
                        rowTimeFormatter.format(Instant.ofEpochMilli(alarm.nextTrigger))
                    ),
                    PdfCell.TextCell(formatOffsetText(alarm.offsetMinutes))
                )
            }

            // Timber.tag(SHARE_PDF_TAG).d( "ROWS_BUILD_DONE rows=${rows.size} time=${WORKER_TIME_FORMATTER.format(Instant.now())} " + "[Next7DaysPdfWorker.kt::doWork]" )

            // -------------------------------------------------
            // 3️⃣ Generate PDF (fixed daily filename)
            // -------------------------------------------------
            Timber.tag(SHARE_PDF_TAG).d("PDF_GENERATION_START filename=Reminders_Next_7_Days.pdf " + "[Next7DaysPdfWorker.kt::doWork]")

            val uri: Uri? = repository.generatePdf(
                title = "Reminders – Next 7 Days",
                headers = listOf("Description", "Trigger Time", "Offset"),
                colWidths = listOf(220f, 200f, 100f),
                rows = rows,
                layout = PdfLayoutConfig(),
                fileName = "Reminders_Next_7_Days.pdf"
            )

            if (uri == null) {
                Timber.tag(SHARE_PDF_TAG).e("PDF_GENERATION_FAILED [Next7DaysPdfWorker.kt::doWork]")
                return Result.success()
            }

            Timber.tag(SHARE_PDF_TAG).i("PDF_GENERATED uri=$uri time=${WORKER_TIME_FORMATTER.format(Instant.now())} " + "[Next7DaysPdfWorker.kt::doWork]")

            // -------------------------------------------------
            // 4️⃣ Show notification (Share + Dismiss)
            // -------------------------------------------------
            showNotification(uri)

            Timber.tag(SHARE_PDF_TAG).i("WORK_COMPLETE_SUCCESS [Next7DaysPdfWorker.kt::doWork]")

        } catch (t: Throwable) {
            Timber.tag(SHARE_PDF_TAG).e(t, "WORK_FAILED_EXCEPTION [Next7DaysPdfWorker.kt::doWork]")
        }

        return Result.success()
    }

    // =========================================================
    // Notification (Share + Dismiss)
    // =========================================================
    /**
     * Caller:
     *  - doWork()
     *
     * Responsibility:
     *  - Displays notification with Share + Dismiss actions.
     *  - Ensures notification channel exists.
     *
     * Side Effects:
     *  - Posts system notification.
     */
    private fun showNotification(uri: Uri) {

        //Timber.tag(SHARE_PDF_TAG).d("NOTIFICATION_BUILD_START uri=$uri [Next7DaysPdfWorker.kt::showNotification]")

        val nm =
            applicationContext.getSystemService(NotificationManager::class.java)

        // -----------------------------------------------------
        // 🔔 Ensure notification channel exists (Android 8+)
        // -----------------------------------------------------
        ensureNotificationChannel(nm)

        // -----------------------------------------------------
        // Share PDF
        // -----------------------------------------------------
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Timber.tag(SHARE_PDF_TAG).d("SHARE_INTENT_READY time=${WORKER_TIME_FORMATTER.format(Instant.now())} " + "[Next7DaysPdfWorker.kt::showNotification]" )

        val sharePI = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            Intent.createChooser(shareIntent, "Share PDF"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // -----------------------------------------------------
        // Dismiss → REUSE ReminderReceiver
        // -----------------------------------------------------
        val dismissIntent = Intent(applicationContext, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_DISMISS
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
        }

        // Timber.tag(SHARE_PDF_TAG).d("DISMISS_INTENT_READY notifId=$NOTIFICATION_ID time=${WORKER_TIME_FORMATTER.format(Instant.now())} " + "[Next7DaysPdfWorker.kt::showNotification]" )

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

        Timber.tag(SHARE_PDF_TAG).d("NOTIFICATION_SHOWN time=${WORKER_TIME_FORMATTER.format(Instant.now())} notifId=$NOTIFICATION_ID " + "[Next7DaysPdfWorker.kt::showNotification]")
    }

    // =========================================================
    // Notification Channel (Android 8+ requirement)
    // =========================================================
    /**
     * Caller:
     *  - showNotification()
     *
     * Responsibility:
     *  - Creates notification channel if missing.
     */
    private fun ensureNotificationChannel(nm: NotificationManager) {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            // Timber.tag(SHARE_PDF_TAG).d( "CHANNEL_EXISTS id=$CHANNEL_ID time=${WORKER_TIME_FORMATTER.format(Instant.now())} " + "[Next7DaysPdfWorker.kt::ensureNotificationChannel]" )
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Next 7 Days PDF",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Daily next 7 days reminders PDF"
            enableVibration(true)
        }

        nm.createNotificationChannel(channel)

        // Timber.tag(SHARE_PDF_TAG).d( "CHANNEL_CREATED id=$CHANNEL_ID time=${WORKER_TIME_FORMATTER.format(Instant.now())} " + "[Next7DaysPdfWorker.kt::ensureNotificationChannel]" )
    }

    // =========================================================
    // Offset formatter (shared semantics with UI)
    // =========================================================
    /**
     * Caller:
     *  - doWork()
     *
     * Responsibility:
     *  - Converts offset minutes into readable text.
     */
    private fun formatOffsetText(offsetMinutes: Long): String =
        when {
            offsetMinutes <= 0L -> "on time"
            offsetMinutes % (24 * 60) == 0L -> "${offsetMinutes / (24 * 60)} day before"
            offsetMinutes % 60 == 0L -> "${offsetMinutes / 60} hr before"
            else -> "${offsetMinutes} min before"
        }
}
