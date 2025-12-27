package com.example.eventreminder.ui.viewmodels

// =============================================================
// ReminderViewModel.kt
// - Clean save pipeline (no double-launch)
// - Single DB read for "existing" check
// - ViewModel is the single place for scheduling/cancelling alarms
// - Assumes repository is DB-only and verifies writes before returning
// - Detailed SaveReminderLogs for tracing the entire flow
// =============================================================

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.model.ReminderOffset
import com.example.eventreminder.data.model.ReminderTitle
import com.example.eventreminder.data.model.RepeatRule
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.scheduler.ReminderSchedulingEngine
import com.example.eventreminder.sync.core.SyncEngine
import com.example.eventreminder.util.NextOccurrenceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import com.example.eventreminder.logging.DELETE_TAG
import com.example.eventreminder.logging.SAVE_TAG


private const val TAG = "ReminderViewModel"



@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val schedulingEngine: ReminderSchedulingEngine,
    private val syncEngine: SyncEngine
) : ViewModel() {

    private val deleteInProgress = mutableSetOf<String>()

    // SNACKBAR EVENTS — one-shot channel (no replay after rotation)
    private val _snackbarEvent = Channel<String>(capacity = Channel.BUFFERED)
    val snackbarEvent = _snackbarEvent.receiveAsFlow()


    // ============================================================
    // UI STATE
    // ============================================================
    data class UiState(
        val editReminder: EventReminder? = null,
        val errorMessage: String? = null,
        val title: ReminderTitle = ReminderTitle.EVENT,
        val description: String = "",
        val date: LocalDate = LocalDate.now(),
        val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
        val offsets: Set<ReminderOffset> = emptySet(),
        val repeat: RepeatRule = RepeatRule.NONE
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ============================================================
    // UI UPDATES
    // ============================================================
    fun resetError() = _uiState.update { it.copy(errorMessage = null) }
    fun onTitleChanged(newTitle: ReminderTitle) = _uiState.update { it.copy(title = newTitle) }
    fun onDescriptionChanged(text: String) = _uiState.update { it.copy(description = text) }
    fun onDateChanged(newDate: LocalDate) = _uiState.update { it.copy(date = newDate) }
    fun onTimeChanged(newTime: LocalTime) = _uiState.update { it.copy(time = newTime) }
    fun onOffsetsChanged(newOffsets: Set<ReminderOffset>) = _uiState.update { it.copy(offsets = newOffsets) }
    fun onRepeatChanged(rule: RepeatRule) = _uiState.update { it.copy(repeat = rule) }
    fun clearEditReminder() = _uiState.update { it.copy(editReminder = null) }
    fun resetAddEditForm() { _uiState.value = UiState() }

    // ============================================================
    // LOAD REMINDER (UUID)
    // ============================================================
    fun load(id: String) = viewModelScope.launch {
        try {
            val r = repo.getReminder(id) ?: return@launch

            val zdt = Instant.ofEpochMilli(r.eventEpochMillis)
                .atZone(ZoneId.of(r.timeZone))

            _uiState.update {
                it.copy(
                    editReminder = r,
                    title = ReminderTitle.entries.find { t -> t.label == r.title }
                        ?: ReminderTitle.EVENT,
                    description = r.description ?: "",
                    date = zdt.toLocalDate(),
                    time = zdt.toLocalTime(),
                    offsets = r.reminderOffsets.mapNotNull { ms ->
                        ReminderOffset.fromMillis(ms)
                    }.toSet(),
                    repeat = RepeatRule.fromKey(r.repeatRule)
                )
            }

            Timber.tag(TAG).d("Loaded UUID=$id")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load reminder $id")
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    /*// ============================================================
    // SAVE ENTRY POINT (UUID)
    // - This launches a coroutine and then calls suspend saveReminder()
    // - saveReminder is a suspend function (no nested launches)
    // ============================================================*/
    fun onSaveClicked(
        title: ReminderTitle,
        description: String,
        date: LocalDate,
        time: LocalTime,
        offsets: Set<ReminderOffset>,
        repeatRule: RepeatRule,
        existingId: String?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ) = viewModelScope.launch {
        Timber.tag(SAVE_TAG).d("🔵 UI → Save clicked (existingId=$existingId)")

        val zdt = ZonedDateTime.of(date, time, zoneId)
        val epoch = zdt.toInstant().toEpochMilli()

        val reminder = EventReminder(
            id = existingId ?: UUID.randomUUID().toString(),
            title = title.label,
            description = description.ifBlank { null },
            eventEpochMillis = epoch,
            timeZone = zoneId.id,
            repeatRule = repeatRule.key,
            reminderOffsets = offsets.map { it.millis },
            enabled = true
        )

        Timber.tag(SAVE_TAG).d("🟡 Built EventReminder → $reminder")

        // Call the suspend save flow (single coroutine)
        saveReminder(reminder)
    }

    /*// ============================================================
    // INSERT / UPDATE (UUID)
    // - suspend function (no nested launch)
    // - single DB read to determine isNew
    // - repository is expected to guarantee read-after-write verification
    // - ViewModel delegates scheduling to ReminderSchedulingEngine
    // ============================================================ */
    private suspend fun saveReminder(reminder: EventReminder) {
        Timber.tag(SAVE_TAG).d("🟠 saveReminder() START id=${reminder.id}")

        try {
            // Single read to check existing
            val existing = repo.getReminder(reminder.id)
            val isNew = existing == null

            Timber.tag(SAVE_TAG).d("🟠 IsNew=$isNew existing=$existing")

            val savedId: String =
                if (isNew) {
                    Timber.tag(SAVE_TAG).d("🟠 Inserting reminder…")
                    repo.insert(reminder) // repo returns UUID String (updated.id)
                } else {
                    Timber.tag(SAVE_TAG).d("🟠 Updating reminder…")
                    repo.update(reminder)
                    reminder.id
                }

            Timber.tag(SAVE_TAG).d("🟠 SavedId=$savedId")

            // Load saved (repo should now guarantee the row is visible)
            val saved = repo.getReminder(savedId)

            Timber.tag(SAVE_TAG).d("🟠 Loaded saved reminder → $saved")

            if (saved == null) {
                Timber.tag(SAVE_TAG).e("❌ ERROR → repo returned null after save (id=$savedId)")
                _uiState.update { it.copy(errorMessage = "Failed to save reminder") }
                // Emit an error snackbar so UI knows something went wrong
                _snackbarEvent.trySend("Failed to save reminder")
                return
            }

            // Delegate scheduling to the centralized engine (cancels & schedules internally)
            schedulingEngine.processSavedReminder(saved)

            // Compute next occurrence for snackbar display (view-model responsibility)
            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                saved.eventEpochMillis,
                saved.timeZone,
                saved.repeatRule
            ) ?: saved.eventEpochMillis

            Timber.tag(SAVE_TAG).d("🟠 nextOccurrence=$nextEvent")

            val formatted = Instant.ofEpochMilli(nextEvent)
                .atZone(ZoneId.of(saved.timeZone))
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))

            val msg = "${if (isNew) "Created" else "Updated"}: ${saved.title} → $formatted"

            Timber.tag(SAVE_TAG).d("🟠 Emitting snackbar msg=$msg")
            _snackbarEvent.trySend(msg)

            Timber.tag(SAVE_TAG).d("🟠 Resetting AddEdit form…")
            resetAddEditForm()

        } catch (e: Exception) {
            Timber.tag(SAVE_TAG).e(e, "❌ Exception during saveReminder()")
            Timber.tag(TAG).e(e, "Exception during save process")
            _uiState.update { it.copy(errorMessage = e.message) }
        }

        Timber.tag(SAVE_TAG).d("🟠 saveReminder() END id=${reminder.id}")
    }

    // ============================================================
    // DELETE + UNDO (UUID)
    // ============================================================
    private var recentlyDeleted: EventReminder? = null

    fun deleteEventWithUndo(id: String) = viewModelScope.launch {
        // -------------------------------------------------------
        // DUPLICATE DELETE GUARD
        // -------------------------------------------------------
        if (deleteInProgress.contains(id)) {
            Timber.tag(DELETE_TAG).d("⛔ Duplicate delete ignored id=$id (already in progress)")
            return@launch
        }
        deleteInProgress.add(id)

        Timber.tag(DELETE_TAG).d("🟥 deleteEventWithUndo() START id=$id")

        try {
            val reminder = repo.getReminder(id)
            if (reminder == null) {
                Timber.tag(DELETE_TAG).e("❌ Reminder not found id=$id")
                return@launch
            }

            recentlyDeleted = reminder

            // -------------------------------------------------------
            // STEP 1: Cancel alarms + clear fire-state (ENGINE)
            // -------------------------------------------------------
            Timber.tag(DELETE_TAG).d("➡️ processDelete() via SchedulingEngine id=$id")
            schedulingEngine.processDelete(reminder)
            Timber.tag(DELETE_TAG).d("✔ SchedulingEngine.processDelete completed id=$id")

            // -------------------------------------------------------
            // STEP 2: Soft-delete in DB
            // -------------------------------------------------------
            Timber.tag(DELETE_TAG).d("➡️ Soft delete (repo.markDelete) id=$id")
            repo.markDelete(reminder)
            Timber.tag(DELETE_TAG).d("✔ Soft delete committed id=$id")

            Timber.tag(DELETE_TAG).d("🟥 deleteEventWithUndo() END id=$id")

        } catch (e: Exception) {
            Timber.tag(DELETE_TAG).e(e, "❌ Exception during delete pipeline id=$id")
            _uiState.update { it.copy(errorMessage = e.message) }
        } finally {
            // ALWAYS REMOVE GUARD
            deleteInProgress.remove(id)
        }
    }

    fun restoreLastDeleted() = viewModelScope.launch {
        try {
            val event = recentlyDeleted ?: return@launch
            recentlyDeleted = null

            val restored = event.copy(
                isDeleted = false,
                updatedAt = System.currentTimeMillis()
            )

            Timber.tag(DELETE_TAG).d("↩ Restoring deleted reminder id=${restored.id}")

            // --------------------------------------------
            // STEP 1 — Write restored version to DB
            // --------------------------------------------
            repo.update(restored)

            // --------------------------------------------
            // STEP 2 — Re-schedule alarms for SAME ID
            // --------------------------------------------
            schedulingEngine.processSavedReminder(restored)

            Timber.tag(SAVE_TAG).d("🟢 Undo restore complete → id=${restored.id}")

        } catch (e: Exception) {
            Timber.tag(DELETE_TAG).e(e, "❌ Failed to restore last deleted reminder")
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    // ============================================================
    // BOTTOM TRAY (placeholders)
    // ============================================================

    /*// ============================================================
    // DEBUG / CLEANUP — triggers navigation event
    // ============================================================
    private val _navigateToDebug = Channel<Unit>(capacity = Channel.BUFFERED)
    val navigateToDebug = _navigateToDebug.receiveAsFlow()

    fun cleanupOldReminders() {
        viewModelScope.launch {
            _navigateToDebug.send(Unit)
        }
    }*/

    // generate pdf tray
    fun generatePdfReport() = viewModelScope.launch {}
    fun exportRemindersCsv() = viewModelScope.launch {}

    // ============================================================
    // SYNC + RESCHEDULE
    // - Repo is DB-only. ViewModel delegates rescheduling to engine after sync.
    // ============================================================
    fun syncRemindersWithServer() = viewModelScope.launch {
        try {
            _snackbarEvent.trySend("Sync started…")
            Timber.tag("SYNC").i("Sync started")

            // --------------------------------------------------------
            // Step 1: Perform full remote ↔ local sync
            // --------------------------------------------------------
            val result = syncEngine.syncAll()

            // --------------------------------------------------------
            // Step 2: Re-schedule all enabled reminders
            // --------------------------------------------------------
            val reminders = repo.getNonDeletedEnabled()
            Timber.tag("SYNC").i("Rescheduling ${reminders.size} reminders after sync")

            reminders.forEach { reminder ->
                schedulingEngine.processSavedReminder(reminder)
            }

            // --------------------------------------------------------
            // Step 3: Build snackbar message from SyncResult
            // --------------------------------------------------------
            val message =
                if (result.isEmpty()) {
                    "Sync completed (no changes)"
                } else {
                    buildString {
                        append("Sync completed\n")
                        append("Local → Cloud:  C: ${result.localToRemoteCreated}, " +
                                "U: ${result.localToRemoteUpdated}, " +
                                "D: ${result.localToRemoteDeleted}\n"
                        )
                        append("Cloud → Local:  C: ${result.remoteToLocalCreated}, " +
                                "U: ${result.remoteToLocalUpdated}, " +
                                "D: ${result.remoteToLocalDeleted}"
                        )
                    }
                }

            _snackbarEvent.trySend(message)
            Timber.tag("SYNC").i("Sync completed: $message")

        } catch (e: Exception) {
            _snackbarEvent.trySend("Sync failed: ${e.message}")
            Timber.tag("SYNC").e(e, "Sync failed")
        }
    }

    fun backupReminders(context: Context) {
        viewModelScope.launch {
            val msg = repo.exportRemindersToJson(context)
            _snackbarEvent.trySend(msg)
        }
    }

    fun restoreReminders(context: Context) {
        viewModelScope.launch {
            val msg = repo.restoreRemindersFromBackup(context)
            _snackbarEvent.trySend(msg)
        }
    }
}
