package com.example.eventreminder.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.model.ReminderOffset
import com.example.eventreminder.data.model.ReminderTitle
import com.example.eventreminder.data.model.RepeatRule
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.scheduler.AlarmScheduler
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
 * - Shared between HomeScreen & AddEditReminderScreen
 * - Manages reminder CRUD + alarm scheduling
 * - Emits snackbar messages to HomeScreen using SharedFlow
 * - Groups reminders by date for UI presentation
 */
@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val scheduler: AlarmScheduler
) : ViewModel() {

    // ============================================================
    // 🔔 SNACKBAR EVENTS (SharedFlow)
    // ============================================================

    /**
     * replay = 1 ensures that HomeScreen receives the last snackbar
     * message even after navigating back.
     *
     * We clear the replay manually using clearSnackbar().
     */
    private val _snackbarEvent = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 1
    )
    val snackbarEvent = _snackbarEvent.asSharedFlow()

    fun clearSnackbar() {
        _snackbarEvent.resetReplayCache()
    }

    // ============================================================
    // 🧩 UI State for Add/EditReminderScreen
    // ============================================================

    data class UiState(
        val editReminder: EventReminder? = null,
        val errorMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    fun clearEditReminder() {
        _uiState.value = _uiState.value.copy(editReminder = null)
    }

    fun resetError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    // ============================================================
    // 📅 GROUPED EVENTS FOR HOME SCREEN UI
    // ============================================================

    /**
     * Creates 4 logical groups:
     *  - Today
     *  - Tomorrow
     *  - This Week
     *  - Later
     *
     * Automatically updates when database changes.
     */
    val groupedEvents: StateFlow<List<GroupedUiSection>> =
        repo.getAllReminders()
            .map { list ->
                val now = LocalDate.now()
                val tomorrow = now.plusDays(1)
                val weekEnd = now.with(DayOfWeek.SUNDAY)

                val todayList = mutableListOf<EventReminderUI>()
                val tomorrowList = mutableListOf<EventReminderUI>()
                val weekList = mutableListOf<EventReminderUI>()
                val laterList = mutableListOf<EventReminderUI>()

                list.forEach { ev ->
                    val ui = EventReminderUI.from(
                        id = ev.id,
                        title = ev.title,
                        desc = ev.description,
                        eventMillis = ev.eventEpochMillis,
                        repeat = ev.repeatRule,
                        tz = ev.timeZone
                    )

                    val date = Instant.ofEpochMilli(ev.eventEpochMillis)
                        .atZone(ZoneId.of(ev.timeZone))
                        .toLocalDate()

                    when {
                        date.isEqual(now) -> todayList.add(ui)
                        date.isEqual(tomorrow) -> tomorrowList.add(ui)
                        date.isAfter(tomorrow) && date <= weekEnd -> weekList.add(ui)
                        else -> laterList.add(ui)
                    }
                }

                val groups = mutableListOf<GroupedUiSection>()
                if (todayList.isNotEmpty()) groups.add(GroupedUiSection("Today", todayList.sortedBy { it.eventEpochMillis }))
                if (tomorrowList.isNotEmpty()) groups.add(GroupedUiSection("Tomorrow", tomorrowList.sortedBy { it.eventEpochMillis }))
                if (weekList.isNotEmpty()) groups.add(GroupedUiSection("This Week", weekList.sortedBy { it.eventEpochMillis }))
                if (laterList.isNotEmpty()) groups.add(GroupedUiSection("Later", laterList.sortedBy { it.eventEpochMillis }))

                groups
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ============================================================
    // 📥 LOAD A SINGLE REMINDER (Edit Mode)
    // ============================================================

    fun load(id: Long) = viewModelScope.launch {
        try {
            _uiState.value = _uiState.value.copy(
                editReminder = repo.getReminder(id)
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    // ============================================================
    // 💾 SAVE REMINDER (Insert or Update) + Schedule Alarms
    // Emits snackbar to HomeScreen
    // ============================================================

    fun onSaveClicked(
        title: ReminderTitle,
        description: String,
        date: LocalDate,
        time: LocalTime,
        offsets: Set<ReminderOffset>,
        repeatRule: RepeatRule,
        existingId: Long?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        viewModelScope.launch {

            // Minimal validation
            if (title.label.isBlank()) {
                _uiState.value = _uiState.value.copy(errorMessage = "Title cannot be empty")
                return@launch
            }

            // Compose → VM mapping
            val zdt = ZonedDateTime.of(date, time, zoneId)
            val epoch = zdt.toInstant().toEpochMilli()

            // Build reminder
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

            // ⬅️ IMPORTANT: delegate to your existing logic
            saveReminder(reminder)
        }
    }


    fun saveReminder(reminder: EventReminder) = viewModelScope.launch {
        try {
            val isNew = reminder.id == 0L

            // Insert or update
            val id = if (isNew) repo.insert(reminder) else {
                repo.update(reminder)
                reminder.id
            }

            val saved = repo.getReminder(id)
            if (saved == null) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to save")
                return@launch
            }

            // Determine next event occurrence
            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                saved.eventEpochMillis,
                saved.timeZone,
                saved.repeatRule
            ) ?: saved.eventEpochMillis

            val offsets = saved.reminderOffsets.ifEmpty { listOf(0L) }

            // Cancel old alarms
            scheduler.cancelAll(
                reminderId = id,
                offsets = saved.reminderOffsets
            )

            // Schedule new alarms
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

            // Snackbar text
            val readable = Instant.ofEpochMilli(nextEvent)
                .atZone(ZoneId.of(saved.timeZone))
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))

            val message = "${if (isNew) "Created" else "Updated"}: ${saved.title} → $readable"

            Timber.tag("VM_SNACK").d("Emitting snackbar: $message")
            _snackbarEvent.emit(message)
            Timber.tag("VM_SNACK").d("Emit DONE")

            // Clear edit state
            _uiState.value = UiState()

        } catch (e: Exception) {
            Timber.e(e)
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
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

            scheduler.cancelAll(
                reminderId = id,
                offsets = reminder.reminderOffsets
            )

            repo.delete(reminder)

            Timber.d("Deleted reminder id=$id (Undo available)")
        } catch (e: Exception) {
            Timber.e(e, "deleteEventWithUndo failed")
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    fun restoreLastDeleted() = viewModelScope.launch {
        try {
            val event = recentlyDeleted ?: return@launch
            recentlyDeleted = null

            val newId = repo.insert(event)
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

            Timber.d("Undo → Restored reminder id=$newId")

        } catch (e: Exception) {
            Timber.e(e, "restoreLastDeleted failed")
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    // ============================================================
    // LEGACY DELETE (Only if needed)
    // ============================================================

    fun deleteEvent(id: Long) = viewModelScope.launch {
        try {
            val reminder = repo.getReminder(id) ?: return@launch

            scheduler.cancelAll(
                reminderId = id,
                offsets = reminder.reminderOffsets
            )

            repo.delete(reminder)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    // ============================================================
    // BOTTOM TRAY OPERATIONS (Future extension)
    // ============================================================

    fun cleanupOldReminders() = viewModelScope.launch {
        // TODO
    }

    fun generatePdfReport() = viewModelScope.launch {
        // TODO
    }

    fun exportRemindersCsv() = viewModelScope.launch {
        // TODO
    }

    fun syncRemindersWithServer() = viewModelScope.launch {
        // TODO
    }
}
