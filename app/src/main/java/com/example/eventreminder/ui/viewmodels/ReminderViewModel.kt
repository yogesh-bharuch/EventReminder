package com.example.eventreminder.ui.viewmodels

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
import com.example.eventreminder.util.BackupHelper
import com.example.eventreminder.util.NextOccurrenceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.*
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * ReminderViewModel
 * -----------------
 * Shared between HomeScreen & AddEditReminderScreen
 * Handles:
 *  - CRUD
 *  - alarm scheduling
 *  - undo
 *  - snackbar events
 *  - grouping reminders
 */
@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val scheduler: AlarmScheduler,
    private val syncEngine: SyncEngine
) : ViewModel() {

    // ============================================================
    // 🔔 SNACKBAR EVENTS (SharedFlow)
    // ============================================================

    private val _snackbarEvent = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    fun clearSnackbar() {
        _snackbarEvent.resetReplayCache()
    }

    // ============================================================
    // 🧩 UI STATE (Add/Edit Screen)
    // ============================================================

    data class UiState(
        val editReminder: EventReminder? = null,
        val errorMessage: String? = null,

        // Add/Edit controlled fields
        val title: ReminderTitle = ReminderTitle.EVENT,
        val description: String = "",
        val date: LocalDate = LocalDate.now(),
        val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
        val offsets: Set<ReminderOffset> = emptySet(),
        val repeat: RepeatRule = RepeatRule.NONE
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    fun resetError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // ------------------------------------------------------------
    // UI field update functions
    // ------------------------------------------------------------

    fun onTitleChanged(newTitle: ReminderTitle) =
        _uiState.update { it.copy(title = newTitle) }
    fun onDescriptionChanged(text: String) =
        _uiState.update { it.copy(description = text) }
    fun onDateChanged(newDate: LocalDate) =
        _uiState.update { it.copy(date = newDate) }
    fun onTimeChanged(newTime: LocalTime) =
        _uiState.update { it.copy(time = newTime) }
    fun onOffsetsChanged(newOffsets: Set<ReminderOffset>) =
        _uiState.update { it.copy(offsets = newOffsets) }
    fun onRepeatChanged(rule: RepeatRule) =
        _uiState.update { it.copy(repeat = rule) }
    fun clearEditReminder() =
        _uiState.update { it.copy(editReminder = null) }
    // Clears all Add/Edit fields back to default for "Add new" mode
    // editReminder = null, errorMessage = null, title = EVENT, description = "", date = today, time= now, offsets = emptySet(), repeat = NONE
    fun resetAddEditForm() {
        _uiState.value = UiState()
    }



    // ============================================================
    // 📥 LOAD REMINDER FOR EDIT MODE
    // ============================================================

    fun load(id: Long) = viewModelScope.launch {
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

                    offsets = r.reminderOffsets
                        .mapNotNull { millis -> ReminderOffset.fromMillis(millis) }
                        .toSet(),

                    repeat = RepeatRule.fromKey(r.repeatRule)
                )
            }

            Timber.tag("VM_LOAD").d("Loaded reminder $id → UI fields populated")

        } catch (e: Exception) {
            Timber.e(e, "Failed to load reminder id=$id")
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    // ============================================================
    // 💾 UI → VM SAVE ENTRY POINT (WRAPPER)
    // 💾 SAVE (Insert or Update) + Schedule Alarms
    // ============================================================

    // WRAPPER
    fun onSaveClicked(title: ReminderTitle, description: String, date: LocalDate, time: LocalTime, offsets: Set<ReminderOffset>, repeatRule: RepeatRule, existingId: Long?, zoneId: ZoneId = ZoneId.systemDefault()) = viewModelScope.launch {

        if (title.label.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Title cannot be empty") }
            return@launch
        }

        val zdt = ZonedDateTime.of(date, time, zoneId)
        val epoch = zdt.toInstant().toEpochMilli()

        val reminder = EventReminder(
            id = existingId ?: 0L,
            title = title.label,
            description = description.ifBlank { null },
            eventEpochMillis = epoch,
            timeZone = zoneId.id,
            repeatRule = repeatRule.key,
            reminderOffsets = offsets.map { it.millis },
            enabled = true
        )

        saveReminder(reminder) // delegates to full logic
    }
    // Insert or Update
    fun saveReminder(reminder: EventReminder) = viewModelScope.launch {
        try {
            val isNew = reminder.id == 0L

            val id = if (isNew) repo.insert(reminder) else {
                repo.update(reminder)
                reminder.id
            }

            val saved = repo.getReminder(id)
                ?: return@launch _uiState.update { it.copy(errorMessage = "Failed to save") }

            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                saved.eventEpochMillis,
                saved.timeZone,
                saved.repeatRule
            ) ?: saved.eventEpochMillis

            val offsets = saved.reminderOffsets.ifEmpty { listOf(0L) }

            scheduler.cancelAll(
                reminderId = id,
                offsets = saved.reminderOffsets
            )

            if (saved.enabled) {
                scheduler.scheduleAll(
                    reminderId = id,
                    title = saved.title,
                    message = saved.description ?: "",
                    repeatRule = saved.repeatRule,
                    nextEventTime = nextEvent,
                    offsets = offsets
                )
            }

            val readable = Instant.ofEpochMilli(nextEvent)
                .atZone(ZoneId.of(saved.timeZone))
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))

            val message = "${if (isNew) "Created" else "Updated"}: ${saved.title} → $readable"

            Timber.tag("VM_SNACK").d("Emitting snackbar: $message")
            _snackbarEvent.emit(message)

            resetAddEditForm()

        } catch (e: Exception) {
            Timber.e(e)
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    // ============================================================
    // 🗑 DELETE + UNDO SUPPORT
    // ============================================================

    private var recentlyDeleted: EventReminder? = null

    fun deleteEventWithUndo(id: Long) = viewModelScope.launch {
        try {
            val reminder = repo.getReminder(id) ?: return@launch
            recentlyDeleted = reminder

            scheduler.cancelAll(id, reminder.reminderOffsets)
            repo.markDelete(reminder)

        } catch (e: Exception) {
            Timber.e(e, "deleteEventWithUndo failed")
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    fun restoreLastDeleted() = viewModelScope.launch {
        try {
            val event = recentlyDeleted ?: return@launch
            recentlyDeleted = null

            // IMPORTANT: Reset soft-delete flag before restore
            val restoredReminder = event.copy(isDeleted = false)

            val newId = repo.insert(restoredReminder)
            val restored = repo.getReminder(newId) ?: return@launch

            val offsets = restored.reminderOffsets.ifEmpty { listOf(0L) }
            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                restored.eventEpochMillis,
                restored.timeZone,
                restored.repeatRule
            ) ?: restored.eventEpochMillis

            if (restored.enabled) {
                scheduler.scheduleAll(
                    reminderId = newId,
                    title = restored.title,
                    message = restored.description ?: "",
                    repeatRule = restored.repeatRule,
                    nextEventTime = nextEvent,
                    offsets = offsets
                )
            }

        } catch (e: Exception) {
            Timber.e(e, "restoreLastDeleted failed")
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    // ============================================================
    // BOTTOM TRAY OPERATIONS (Future Enhancements)
    // ============================================================

    fun cleanupOldReminders() = viewModelScope.launch { }
    fun generatePdfReport() = viewModelScope.launch { }
    fun exportRemindersCsv() = viewModelScope.launch { }
    fun syncRemindersWithServer() {
        viewModelScope.launch{
            try {
                Timber.tag("SYNC").d("Sync button clicked")
                _snackbarEvent.emit("Sync started…")
                syncEngine.syncAll()
                _snackbarEvent.emit("Sync completed")
            }
            catch (e: Exception) {
                Timber.e(e, "Sync failed")
                _snackbarEvent.emit("Sync failed: ${e.message}")
            }
        }
    }
    fun backupReminders(context: Context) {
        viewModelScope.launch {
            val resultMessage = repo.exportRemindersToJson(context)
            _snackbarEvent.tryEmit(resultMessage)   // 🔔 show snackbar with count
            Timber.tag("BACKUP").i(resultMessage)   // log again if desired
        }
    }
    fun restoreReminders(context: Context) {
        viewModelScope.launch {
            val resultMessage = repo.restoreRemindersFromBackup(context)
            _snackbarEvent.tryEmit(resultMessage)   // 🔔 show snackbar
            Timber.tag("RESTORE_REMINDERS").i(resultMessage)  // ✅ log again for visibility
        }
    }
}
