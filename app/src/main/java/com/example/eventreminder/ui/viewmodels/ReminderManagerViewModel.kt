// =============================================================
// ReminderManagerViewModel.kt
// =============================================================

package com.example.eventreminder.ui.viewmodels

// =============================================================
// Imports
// =============================================================
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.maintenance.gc.ManualTombstoneGcUseCase
import com.example.eventreminder.maintenance.gc.TombstoneGcReport
import com.example.eventreminder.sync.core.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.example.eventreminder.logging.DELETE_TAG

/**
 * ReminderManagerViewModel
 *
 * RESPONSIBILITIES:
 * 1. Normalize reminder data (repeatRule consistency)
 * 2. Convert expired one-time reminders into TOMBSTONES
 * 3. SYNC tombstones to remote (CRITICAL ORDER)
 * 4. Run tombstone garbage collection (local + remote)
 *
 * GOLDEN RULE:
 * A tombstone MUST be synced before it is eligible for GC.
 */
@HiltViewModel
class ReminderManagerViewModel @Inject constructor(
    private val manualTombstoneGcUseCase: ManualTombstoneGcUseCase,
    private val reminderRepository: ReminderRepository,
    private val syncEngine: SyncEngine
) : ViewModel() {

    private val _gcReport = MutableStateFlow<TombstoneGcReport?>(null)
    val gcReport: StateFlow<TombstoneGcReport?> = _gcReport

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    /**
     * Runs full reminder cleanup.
     *
     * PIPELINE:
     * Phase 0 → Normalize DB
     * Phase 1 → Mark expired one-time reminders as TOMBSTONES
     * Phase 1.5 → Sync tombstones to remote (🔥 REQUIRED)
     * Phase 2 → Garbage collect tombstones
     */
    fun runFullCleanup(retentionDays: Int) {
        if (_isRunning.value) return
        _isRunning.value = true

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val cutoffMillis = now - retentionDays * 24L * 60 * 60 * 1000

                /*// =====================================================
                // Phase 0 — Normalize DB
                // Purpose:
                //   Ensure repeatRule consistency ("" → NULL)
                //   This prevents edge-case mismatches in cleanup logic.
                // =====================================================*/
                Timber.tag(DELETE_TAG).i("CLEANUP Phase 0 → Normalize DB (repeatRule \"\" → NULL)")
                reminderRepository.normalizeRepeatRules()

                /*// =====================================================
                // Phase 1 — Identify expired ONE-TIME reminders
                //
                // Criteria:
                // - enabled = false        (already fired)
                // - repeatRule = null     (one-time only)
                // - updatedAt < cutoff    (outside retention window)
                // =====================================================*/
                val allReminders = reminderRepository.getAllReminders().first()

                val deletable = allReminders.filter { reminder ->
                    !reminder.enabled &&
                            reminder.repeatRule == null &&
                            reminder.updatedAt < cutoffMillis
                }

                Timber.tag(DELETE_TAG).i("CLEANUP Phase 1 → Found %d expired one-time reminders", deletable.size)

                /*// =====================================================
                // Phase 1 — MARK TOMBSTONES (LOCAL ONLY)
                //
                // IMPORTANT:
                // This converts expired reminders into tombstones
                // (isDeleted = true).
                //
                // DO NOT GC yet.
                // =====================================================*/
                deletable.forEach { reminder ->
                    Timber.tag(DELETE_TAG).d("CLEANUP → Mark tombstone id=%s", reminder.id)
                    reminderRepository.markDelete(reminder)
                }

                /*// =====================================================
                // Phase 1.5 — SYNC TOMBSTONES TO REMOTE (🔥 CRITICAL)
                //
                // WHY THIS MUST HAPPEN HERE:
                // - GC must NEVER run before tombstones are synced
                // - Otherwise remote orphans are created
                // =====================================================*/
                Timber.tag(DELETE_TAG).i("CLEANUP Phase 1.5 → Syncing tombstones to remote")
                syncEngine.syncAll()

                /*// =====================================================
                // Phase 2 — Tombstone Garbage Collection
                //
                // This is SAFE now because:
                // - Tombstones already exist locally
                // - Tombstones are already synced to remote
                // =====================================================*/
                Timber.tag(DELETE_TAG).i("CLEANUP Phase 2 → Running tombstone GC (retentionDays=%d)", retentionDays)

                val report = manualTombstoneGcUseCase.run(
                    nowEpochMillis = now,
                    retentionDays = retentionDays
                )

                _gcReport.value = report

            } finally {
                _isRunning.value = false
            }
        }
    }

    // =============================================================
    // CLEANUP — PHASE A: TOMBSTONE PROPAGATION (SAFE)
    // =============================================================
    /**
     * Phase A — Propagate tombstones.
     *
     * PURPOSE:
     * - Convert expired one-time reminders into TOMBSTONES
     * - Sync tombstones to Firestore
     *
     * SAFETY:
     * - Multi-device safe
     * - Can be run multiple times
     * - MUST run before remote GC
     */
    fun propagateExpiredTombstones(retentionDays: Int) {
        if (_isRunning.value) return
        _isRunning.value = true

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val cutoffMillis = now - retentionDays * 24L * 60 * 60 * 1000

                // -----------------------------------------------------
                // Phase 0 — Normalize DB
                // -----------------------------------------------------
                Timber.tag(DELETE_TAG)
                    .i("CLEANUP[A] Normalize DB (repeatRule \"\" → NULL)")
                reminderRepository.normalizeRepeatRules()

                // -----------------------------------------------------
                // Phase 1 — Identify expired ONE-TIME reminders
                // -----------------------------------------------------
                val allReminders = reminderRepository.getAllReminders().first()

                val expired = allReminders.filter { reminder ->
                    !reminder.enabled &&
                            reminder.repeatRule == null &&
                            reminder.updatedAt < cutoffMillis &&
                            !reminder.isDeleted
                }

                Timber.tag(DELETE_TAG)
                    .i("CLEANUP[A] Found %d expired reminders to tombstone", expired.size)

                // -----------------------------------------------------
                // Phase 1 — Mark TOMBSTONES locally
                // -----------------------------------------------------
                expired.forEach { reminder ->
                    Timber.tag(DELETE_TAG)
                        .d("CLEANUP[A] Mark tombstone id=%s", reminder.id)
                    reminderRepository.markDelete(reminder)
                }

                // -----------------------------------------------------
                // Phase 1.5 — Sync tombstones to remote
                // -----------------------------------------------------
                Timber.tag(DELETE_TAG)
                    .i("CLEANUP[A] Syncing tombstones to Firestore")
                syncEngine.syncAll()

            } finally {
                _isRunning.value = false
            }
        }
    }

    // =============================================================
    // CLEANUP — PHASE B: TOMBSTONE GARBAGE COLLECTION (DANGEROUS)
    // =============================================================
    /**
     * Phase B — Hard delete tombstones.
     *
     * * ⚠️ WARNING:
     *      * - MUST be executed only AFTER all devices have synced tombstones
     *      * - Running too early can cause remote resurrection
     *
     * PURPOSE:
     * - Permanently delete tombstones from:
     *   • Local DB
     *   • Firestore
     *
     */
    fun runRemoteTombstoneGc(retentionDays: Int) {
        if (_isRunning.value) return
        _isRunning.value = true

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()

                Timber.tag(DELETE_TAG)
                    .i("CLEANUP[B] Running tombstone GC (retentionDays=%d)", retentionDays)

                val report = manualTombstoneGcUseCase.run(
                    nowEpochMillis = now,
                    retentionDays = retentionDays
                )

                _gcReport.value = report

            } finally {
                _isRunning.value = false
            }
        }
    }


}
