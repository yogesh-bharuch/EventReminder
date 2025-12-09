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
import com.example.eventreminder.util.NextOccurrenceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val scheduler: AlarmScheduler,
    private val syncEngine: SyncEngine
) : ViewModel() {

    // ============================================================
    // SNACKBAR EVENTS
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
    val uiState: StateFlow<UiState> = _uiState

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

            Timber.tag("VM_LOAD").d("Loaded UUID=$id")

        } catch (e: Exception) {
            Timber.e(e)
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    // ============================================================
    // SAVE ENTRY POINT (UUID)
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

        saveReminder(reminder)
    }

    // ============================================================
    // INSERT / UPDATE (UUID)
    // ============================================================
    fun saveReminder(reminder: EventReminder) = viewModelScope.launch {
        try {
            val isNew = repo.getReminder(reminder.id) == null

            val savedId =
                if (isNew) repo.insert(reminder)
                else {
                    repo.update(reminder)
                    reminder.id
                }

            val saved = repo.getReminder(savedId)
                ?: return@launch _uiState.update { it.copy(errorMessage = "Failed to save") }

            // Compute next occurrence
            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                saved.eventEpochMillis,
                saved.timeZone,
                saved.repeatRule
            ) ?: saved.eventEpochMillis

            val offsets = saved.reminderOffsets.ifEmpty { listOf(0L) }

            // Cancel old alarms (UUID)
            scheduler.cancelAllByString(
                reminderIdString = savedId,
                offsets = saved.reminderOffsets
            )

            // Schedule new alarms (UUID)
            if (saved.enabled) {
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
            _snackbarEvent.emit(msg)

            resetAddEditForm()

        } catch (e: Exception) {
            Timber.e(e)
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    // ============================================================
    // DELETE + UNDO (UUID)
    // ============================================================
    private var recentlyDeleted: EventReminder? = null

    fun deleteEventWithUndo(id: String) = viewModelScope.launch {
        try {
            val reminder = repo.getReminder(id) ?: return@launch
            recentlyDeleted = reminder

            scheduler.cancelAllByString(id, reminder.reminderOffsets)
            repo.markDelete(reminder)

        } catch (e: Exception) {
            Timber.e(e)
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    fun restoreLastDeleted() = viewModelScope.launch {
        try {
            val event = recentlyDeleted ?: return@launch
            recentlyDeleted = null

            val restored = event.copy(isDeleted = false)

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

        } catch (e: Exception) {
            Timber.e(e)
            _uiState.update { it.copy(errorMessage = e.message) }
        }
    }

    // ============================================================
    // BOTTOM TRAY
    // ============================================================
    fun cleanupOldReminders() = viewModelScope.launch {}
    fun generatePdfReport() = viewModelScope.launch {}
    fun exportRemindersCsv() = viewModelScope.launch {}

    fun syncRemindersWithServer() = viewModelScope.launch {
        try {
            _snackbarEvent.emit("Sync started…")

            // 🟦 Step 1: Perform full remote ↔ local sync
            syncEngine.syncAll()

            // 🟩 Step 2: Re-schedule all reminders using updated synced data
            repo.rescheduleAllAfterSync()

            _snackbarEvent.emit("Sync completed")
            Timber.tag("SYNC").i("Sync + Reschedule completed successfully")

        } catch (e: Exception) {
            _snackbarEvent.emit("Sync failed: ${e.message}")
            Timber.tag("SYNC").e(e, "Sync failed")
        }
    }


    fun backupReminders(context: Context) {
        viewModelScope.launch {
            val msg = repo.exportRemindersToJson(context)
            _snackbarEvent.tryEmit(msg)
        }
    }

    fun restoreReminders(context: Context) {
        viewModelScope.launch {
            val msg = repo.restoreRemindersFromBackup(context)
            _snackbarEvent.tryEmit(msg)
        }
    }
}
