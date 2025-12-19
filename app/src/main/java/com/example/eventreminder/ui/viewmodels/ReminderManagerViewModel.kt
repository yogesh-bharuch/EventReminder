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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import com.example.eventreminder.logging.DELETE_TAG
import com.example.eventreminder.sync.core.SyncEngine

/**
 * ViewModel for ReminderManagerScreen.
 *
 * Responsibilities:
 * 1. Expire past one-time reminders (grace-period based)
 * 2. Run tombstone garbage collection
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
     * Runs full reminder cleanup:
     * 1. Normalize DB ("" → NULL)
     * 2. Delete expired one-time reminders older than retention window
     * 3. Run tombstone GC
     */
    fun runFullCleanup(retentionDays: Int) {
        if (_isRunning.value) return
        _isRunning.value = true

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cutoffMillis = now - retentionDays * 24L * 60 * 60 * 1000

            // =====================================================
            // Phase 0 — Normalize DB
            // =====================================================
            Timber.tag(DELETE_TAG).i("Normalize DB (repeatRule \"\" → NULL)")
            reminderRepository.normalizeRepeatRules()

            // =====================================================
            // Phase 1 — DELETE expired one-time reminders
            // =====================================================
            val allReminders = reminderRepository.getAllReminders().first()

            val deletable = allReminders.filter { reminder ->
                !reminder.enabled &&
                        reminder.repeatRule == null &&
                        reminder.updatedAt < cutoffMillis
            }

            Timber.tag(DELETE_TAG).i("Triggering sync after cleanup tombstones")
            syncEngine.syncAll()

            Timber.tag(DELETE_TAG).i("Marking %d expired one-time reminders as deleted", deletable.size)
            Timber.tag("ReminderManager").i("Deleting %d expired one-time reminders", deletable.size)

            deletable.forEach { reminder ->
                Timber.tag(DELETE_TAG).d("Mark delete → id=%s", reminder.id)
                reminderRepository.markDelete(reminder)
            }

            // =====================================================
            // Phase 2 — Tombstone GC
            // =====================================================
            val report = manualTombstoneGcUseCase.run(
                nowEpochMillis = now,
                retentionDays = retentionDays
            )

            _gcReport.value = report
            _isRunning.value = false
        }
    }
}