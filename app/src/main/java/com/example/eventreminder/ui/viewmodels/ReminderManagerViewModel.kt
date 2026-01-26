package com.example.eventreminder.ui.viewmodels

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.maintenance.gc.ManualTombstoneGcUseCase
import com.example.eventreminder.maintenance.gc.TombstoneGcReport
import com.example.eventreminder.sync.core.SyncEngine
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.example.eventreminder.logging.DELETE_TAG
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * ReminderManagerViewModel
 *
 * RESPONSIBILITIES:
 * 1. Normalize reminder data
 * 2. Convert expired one-time reminders into TOMBSTONES
 * 3. Sync tombstones to remote (CRITICAL ORDER)
 * 4. Run tombstone garbage collection (local + remote)
 *
 * GOLDEN RULE:
 * A tombstone MUST be synced before it is eligible for GC.
 *
 * CLOUD SAFETY RULE:
 * - Any Firebase operation MUST be guarded by FirebaseAuth presence
 */
@HiltViewModel
class ReminderManagerViewModel @Inject constructor(
    private val manualTombstoneGcUseCase: ManualTombstoneGcUseCase,
    private val reminderRepository: ReminderRepository,
    private val syncEngine: SyncEngine,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _gcReport = MutableStateFlow<TombstoneGcReport?>(null)
    val gcReport: StateFlow<TombstoneGcReport?> = _gcReport

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    // =============================================================
    // CLOUD GUARD
    // =============================================================

    private enum class CloudGate {
        NOT_LOGGED_IN,
        EMAIL_NOT_VERIFIED
    }

    /**
     * Returns null if cloud operations are allowed.
     * Otherwise returns the blocking reason.
     */
    private fun checkCloudCapability(): CloudGate? {
        val user = FirebaseAuth.getInstance().currentUser
            ?: return CloudGate.NOT_LOGGED_IN

        if (!user.isEmailVerified) {
            return CloudGate.EMAIL_NOT_VERIFIED
        }

        return null
    }

    private suspend fun abortIfCloudBlocked(): Boolean {
        return when (checkCloudCapability()) {
            CloudGate.NOT_LOGGED_IN -> {
                Timber.tag(DELETE_TAG).w("CLEANUP blocked → Firebase user not logged in [ReminderManagerViewModel.kt::abortIfCloudBlocked]")
                Toast.makeText(context, "Cleanup blocked: Firebase user not logged in", Toast.LENGTH_SHORT).show()
                true
            }

            CloudGate.EMAIL_NOT_VERIFIED -> {
                Timber.tag(DELETE_TAG).w("CLEANUP blocked → Email not verified [ReminderManagerViewModel.kt::abortIfCloudBlocked]")
                Toast.makeText(context, "Cleanup blocked: Email not verified", Toast.LENGTH_SHORT).show()

                true
            }

            null -> false
        }
    }

    // =============================================================
    // FULL CLEANUP PIPELINE (USES SYNC → MUST GUARD)
    // =============================================================

    fun runFullCleanup(retentionDays: Int) {
        if (_isRunning.value) return
        _isRunning.value = true

        viewModelScope.launch {
            try {
                if (abortIfCloudBlocked()) return@launch

                val now = System.currentTimeMillis()
                val cutoffMillis = now - retentionDays * 24L * 60 * 60 * 1000

                Timber.tag(DELETE_TAG).i("CLEANUP Phase 0 → Normalize DB")
                reminderRepository.normalizeRepeatRules()

                val allReminders = reminderRepository.getAllReminders().first()

                val deletable = allReminders.filter { reminder ->
                    !reminder.enabled &&
                            reminder.repeatRule == null &&
                            reminder.updatedAt < cutoffMillis
                }

                Timber.tag(DELETE_TAG).i("CLEANUP Phase 1 → Found %d expired reminders", deletable.size)

                deletable.forEach { reminder ->
                    Timber.tag(DELETE_TAG).d("CLEANUP → Mark tombstone id=%s", reminder.id)
                    reminderRepository.markDelete(reminder)
                }

                Timber.tag(DELETE_TAG).i("CLEANUP Phase 1.5 → Syncing tombstones to remote")
                syncEngine.syncAll()

                Timber.tag(DELETE_TAG).i("CLEANUP Phase 2 → Running tombstone GC")

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
    // PHASE A — PROPAGATE TOMBSTONES (USES SYNC → MUST GUARD)
    // =============================================================

    fun propagateExpiredTombstones(retentionDays: Int) {
        if (_isRunning.value) return
        _isRunning.value = true

        viewModelScope.launch {
            try {
                if (abortIfCloudBlocked()) return@launch

                val now = System.currentTimeMillis()
                val cutoffMillis = now - retentionDays * 24L * 60 * 60 * 1000

                Timber.tag(DELETE_TAG).i("CLEANUP[A] Normalize DB")
                reminderRepository.normalizeRepeatRules()

                val allReminders = reminderRepository.getAllReminders().first()

                val expired = allReminders.filter { reminder ->
                    !reminder.enabled &&
                            reminder.repeatRule == null &&
                            reminder.updatedAt < cutoffMillis &&
                            !reminder.isDeleted
                }

                Timber.tag(DELETE_TAG).i("CLEANUP[A] Found %d expired reminders", expired.size)

                expired.forEach { reminder ->
                    Timber.tag(DELETE_TAG).d("CLEANUP[A] Mark tombstone id=%s", reminder.id)
                    reminderRepository.markDelete(reminder)
                }

                Timber.tag(DELETE_TAG).i("CLEANUP[A] Syncing tombstones to Firestore")
                syncEngine.syncAll()

            } finally {
                _isRunning.value = false
            }
        }
    }

    // =============================================================
    // PHASE B — REMOTE GC (🔥 DANGEROUS → MUST GUARD)
    // =============================================================

    fun runRemoteTombstoneGc(retentionDays: Int) {
        if (_isRunning.value) return
        _isRunning.value = true

        viewModelScope.launch {
            try {
                if (abortIfCloudBlocked()) return@launch

                val now = System.currentTimeMillis()

                Timber.tag(DELETE_TAG).i("CLEANUP[B] Running tombstone GC (retentionDays=%d)", retentionDays)

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
