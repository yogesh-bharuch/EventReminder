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
import com.example.eventreminder.scheduler.AlarmScheduler
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

private const val TAG = "ReminderViewModel"
private const val SAVE_TAG = "SaveReminderLogs"

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val scheduler: AlarmScheduler,
    private val syncEngine: SyncEngine
) : ViewModel() {

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

    // ============================================================
    // SAVE ENTRY POINT (UUID)
    // - This launches a coroutine and then calls suspend saveReminder()
    // - saveReminder is a suspend function (no nested launches)
    // ============================================================
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

    // ============================================================
    // INSERT / UPDATE (UUID)
    // - suspend function (no nested launch)
    // - single DB read to determine isNew
    // - repository is expected to guarantee read-after-write verification
    // - ViewModel responsible for scheduling/cancelling alarms only
    // ============================================================
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

            // Compute next occurrence (view-model responsibility)
            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                saved.eventEpochMillis,
                saved.timeZone,
                saved.repeatRule
            ) ?: saved.eventEpochMillis

            Timber.tag(SAVE_TAG).d("🟠 nextOccurrence=$nextEvent")

            val offsets = saved.reminderOffsets.ifEmpty { listOf(0L) }

            // Cancel OLD alarms (only here in ViewModel)
            Timber.tag(SAVE_TAG).d("🟠 Canceling old alarms for $savedId …")
            scheduler.cancelAllByString(
                reminderIdString = savedId,
                offsets = saved.reminderOffsets
            )

            // Schedule NEW alarms (only here in ViewModel)
            if (saved.enabled) {
                Timber.tag(SAVE_TAG).d("🟠 Scheduling alarms → offsets=$offsets")
                scheduler.scheduleAllByString(
                    reminderIdString = savedId,
                    title = saved.title,
                    message = saved.description ?: "",
                    repeatRule = saved.repeatRule,
                    nextEventTime = nextEvent,
                    offsets = offsets
                )
            }

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
        try {
            val reminder = repo.getReminder(id) ?: return@launch
            recentlyDeleted = reminder

            // Cancel alarms before marking deleted (ViewModel responsibility)
            scheduler.cancelAllByString(reminderIdString = id, offsets = reminder.reminderOffsets)
            repo.markDelete(reminder)

            Timber.tag(SAVE_TAG).d("🟠 Deleted (soft) id=$id")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to delete reminder id=$id")
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    fun restoreLastDeleted() = viewModelScope.launch {
        try {
            val event = recentlyDeleted ?: return@launch
            recentlyDeleted = null

            val restored = event.copy(isDeleted = false, updatedAt = System.currentTimeMillis())

            // Insert restored and schedule from ViewModel
            val newId = repo.insert(restored)
            val saved = repo.getReminder(newId) ?: return@launch

            val offsets = saved.reminderOffsets.ifEmpty { listOf(0L) }
            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                saved.eventEpochMillis,
                saved.timeZone,
                saved.repeatRule
            ) ?: saved.eventEpochMillis

            if (saved.enabled) {
                scheduler.scheduleAllByString(
                    reminderIdString = newId,
                    title = saved.title,
                    message = saved.description ?: "",
                    repeatRule = saved.repeatRule,
                    nextEventTime = nextEvent,
                    offsets = offsets
                )
            }

            Timber.tag(SAVE_TAG).d("🟠 Restored deleted reminder id=$newId")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to restore last deleted reminder")
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    // ============================================================
    // BOTTOM TRAY (placeholders)
    // ============================================================
    fun cleanupOldReminders() = viewModelScope.launch {}
    fun generatePdfReport() = viewModelScope.launch {}
    fun exportRemindersCsv() = viewModelScope.launch {}

    // ============================================================
    // SYNC + RESCHEDULE
    // - Repo is DB-only. ViewModel must reschedule alarms after sync.
    // ============================================================
    fun syncRemindersWithServer() = viewModelScope.launch {
        try {
            _snackbarEvent.trySend("Sync started…")
            Timber.tag("SYNC").i("Sync started")

            // Step 1: Perform full remote ↔ local sync
            syncEngine.syncAll()

            // Step 2: Re-schedule all reminders using updated synced data (ViewModel)
            val reminders = repo.getNonDeletedEnabled()
            Timber.tag("SYNC").i("Rescheduling ${reminders.size} reminders after sync")

            reminders.forEach { reminder ->
                // Cancel previous then schedule new using nextOccurrence
                scheduler.cancelAllByString(reminderIdString = reminder.id, offsets = reminder.reminderOffsets)

                val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                    reminder.eventEpochMillis,
                    reminder.timeZone,
                    reminder.repeatRule
                ) ?: reminder.eventEpochMillis

                if (reminder.enabled) {
                    scheduler.scheduleAllByString(
                        reminderIdString = reminder.id,
                        title = reminder.title,
                        message = reminder.description ?: "",
                        repeatRule = reminder.repeatRule,
                        nextEventTime = nextEvent,
                        offsets = reminder.reminderOffsets.ifEmpty { listOf(0L) }
                    )
                }
            }

            _snackbarEvent.trySend("Sync completed")
            Timber.tag("SYNC").i("Sync + Reschedule completed successfully")

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
