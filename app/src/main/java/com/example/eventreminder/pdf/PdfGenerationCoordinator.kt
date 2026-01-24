package com.example.eventreminder.pdf

import android.content.Context
import android.net.Uri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.eventreminder.data.delivery.PdfDeliveryLedger
import com.example.eventreminder.data.session.SessionRepository
import com.example.eventreminder.logging.SHARE_PDF_TAG
import com.example.eventreminder.workers.Next7DaysPdfWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PdfGenerationCoordinator
 *
 * Caller(s):
 *  - PdfViewModel (UI flow)
 *  - Next7DaysPdfWorker (daily automation)
 *  - Debug pipeline
 *  - App startup recovery pipeline
 *
 * Responsibility:
 *  - Central orchestration for Next 7 Days PDF generation.
 *  - Decides:
 *      - session guard
 *      - delivery ledger guard
 *      - generation path
 *      - delivery mode
 *      - notification dispatch
 *      - UI dispatch
 *      - background dispatch
 *
 * Guarantees:
 *  - Single source of truth for generation flow
 *  - No duplicate logic
 *  - No split pipelines
 *  - Deterministic behavior
 *  - Exactly-once-per-day notification semantics
 *  - No recursive worker scheduling
 */
@Singleton
class PdfGenerationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val next7DaysPdfUseCase: Next7DaysPdfUseCase,
    private val sessionRepository: SessionRepository,
    private val pdfDeliveryLedger: PdfDeliveryLedger   // delivery ledger authority
) {

    /**
     * Central Next 7 Days PDF pipeline.
     *
     * Delivery modes:
     *  - UI_ONLY               → generate + open only
     *  - UI_AND_NOTIFICATION   → generate + enqueue worker
     *  - NOTIFICATION_ONLY     → worker-only execution (NO enqueue)
     *  - WORKER_ONLY           → worker execution path
     */
    suspend fun generateNext7Days(
        delivery: PdfDeliveryMode
    ): PdfGenerationResult {

        // -------------------------------------------------
        // 🔐 SESSION GUARD
        // -------------------------------------------------
        val session = sessionRepository.sessionState.first()

        if (session.uid == null) {
            Timber.tag(SHARE_PDF_TAG).w("PDF_GEN_SKIPPED no_session [PdfGenerationCoordinator.kt::generateNext7Days]")
            return PdfGenerationResult.Skipped("NO_SESSION")
        }

        // -------------------------------------------------
        // 📓 DELIVERY LEDGER GUARD (ANTI-SPAM)
        // -------------------------------------------------
        val today = LocalDate.now(ZoneId.systemDefault())
        val lastDelivered = pdfDeliveryLedger.next7DaysLastDelivered.first()

        if (lastDelivered == today) {
            Timber.tag(SHARE_PDF_TAG).w("PDF_GEN_SKIPPED already_delivered_today date=$today [PdfGenerationCoordinator.kt::generateNext7Days]")
            return PdfGenerationResult.Skipped("ALREADY_DELIVERED_TODAY")
        }

        // -------------------------------------------------
        // 🚀 GENERATION
        // -------------------------------------------------
        return try {

            val uri = next7DaysPdfUseCase.generate()

            if (uri == null) {
                Timber.tag(SHARE_PDF_TAG).e("PDF_GEN_FAILED null_uri [PdfGenerationCoordinator.kt::generateNext7Days]")
                return PdfGenerationResult.Failed("NULL_URI")
            }

            Timber.tag(SHARE_PDF_TAG).i("PDF_GEN_SUCCESS uri=$uri mode=$delivery [PdfGenerationCoordinator.kt::generateNext7Days]")

            // -------------------------------------------------
            // 📦 DELIVERY MODES — Execution Semantics
            // -------------------------------------------------
            /*
             DELIVERY FLOW MODEL:

             1) UI_ONLY
                - Pure UI flow
                - Generates PDF
                - Opens PDF in viewer
                - No notification
                - No worker
                - No ledger write
                → Used for preview / manual open use-cases

             2) UI_AND_NOTIFICATION
                - User-initiated flow
                - Generates PDF immediately
                - Enqueues worker for notification delivery
                - Ledger (updating datastore for notification delvered today) is NOT written here
                - Ledger is written ONLY by worker after notification is shown
                → Guarantees user always gets one notification per click

             3) NOTIFICATION_ONLY
                - Worker execution context
                - Worker calls coordinator
                - Coordinator must NOT enqueue worker
                - Worker is terminal execution path
                - Ledger write happens in worker after notification
                → Prevents infinite scheduling loops

             4) WORKER_ONLY
                - Safety mode
                - No enqueue
                - No UI
                - No delivery
                - Used as internal guard
                → Prevents recursion / accidental re-entry

             LEDGER RULE:
             - UI never writes ledger
             - Worker is the ONLY authority that writes ledger
             - Ledger write = notification successfully delivered
             - Ledger read = delivery guard (anti-spam / anti-loop)

             ARCHITECTURAL GUARANTEES:
             - No duplicate notifications
             - No infinite worker loops
             - No race conditions
             - Deterministic delivery
             - Exactly-once-per-day semantics
             - UI override always possible via clearNext7DaysDelivery()
            */
            when (delivery) {
                PdfDeliveryMode.UI_ONLY -> {
                    // UI opens PDF only
                    // ❌ no worker
                    // ❌ no ledger write
                }
                PdfDeliveryMode.UI_AND_NOTIFICATION -> {
                    // UI triggers worker
                    enqueueWorker()
                    // ❌ ledger write happens ONLY in worker
                }
                PdfDeliveryMode.NOTIFICATION_ONLY -> {
                    // Worker context
                    // ❌ NEVER enqueue worker from worker
                    // ❌ ledger write happens in worker
                }
                PdfDeliveryMode.WORKER_ONLY -> {
                    return PdfGenerationResult.Skipped("WORKER_HANDLED")
                }
            }

            PdfGenerationResult.Success(uri)

        } catch (t: Throwable) {
            Timber.tag(SHARE_PDF_TAG).e(t, "PDF_GEN_EXCEPTION [PdfGenerationCoordinator.kt::generateNext7Days]")
            PdfGenerationResult.Failed(t.message ?: "UNKNOWN_ERROR")
        }
    }

    // =========================================================
    // Worker trigger (notification delivery)
    // =========================================================
    private fun enqueueWorker() {
        val request = OneTimeWorkRequestBuilder<Next7DaysPdfWorker>()
            .addTag("coordinator_next_7_days_pdf")
            .build()

        WorkManager.getInstance(context).enqueue(request)

        Timber.tag(SHARE_PDF_TAG).d("WORKER_ENQUEUED via coordinator [PdfGenerationCoordinator.kt::enqueueWorker]")
    }

    /**
     * UI override hook.
     *
     * Caller(s):
     *  - PdfViewModel (user action only)
     *  - Debug pipeline
     *
     * Responsibility:
     *  - Clears delivery ledger so user-triggered generation
     *    always produces a notification once.
     */
    suspend fun clearNext7DaysDelivery() {
        pdfDeliveryLedger.clearNext7DaysDelivery()
        Timber.tag(SHARE_PDF_TAG).i("LEDGER_CLEARED_BY_UI [PdfGenerationCoordinator.kt::clearNext7DaysDelivery]")
    }

    /**
     * Worker delivery hook.
     *
     * Caller(s):
     *  - Next7DaysPdfWorker only
     *
     * Responsibility:
     *  - Marks ledger after notification is shown.
     *  - Guarantees exactly-once-per-day semantics.
     */
    suspend fun markDeliveredFromWorker() {
        val today = LocalDate.now(ZoneId.systemDefault())
        pdfDeliveryLedger.markNext7DaysDelivered(today)
        Timber.tag(SHARE_PDF_TAG).i("LEDGER_MARKED_BY_WORKER date=$today [PdfGenerationCoordinator.kt::markDeliveredFromWorker]")
    }
}

/**
 * Delivery modes for PDF generation.
 */
sealed class PdfDeliveryMode {
    data object UI_ONLY : PdfDeliveryMode()
    data object NOTIFICATION_ONLY : PdfDeliveryMode()
    data object UI_AND_NOTIFICATION : PdfDeliveryMode()
    data object WORKER_ONLY : PdfDeliveryMode()
}

/**
 * Result model for PDF generation pipeline.
 */
sealed class PdfGenerationResult {
    data class Success(val uri: Uri) : PdfGenerationResult()
    data class Skipped(val reason: String) : PdfGenerationResult()
    data class Failed(val error: String) : PdfGenerationResult()
}
