package com.example.eventreminder.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.scheduler.AlarmScheduler
import com.example.eventreminder.util.NextOccurrenceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.*
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val scheduler: AlarmScheduler
) : ViewModel() {

    // ============================================================
    // UI State
    // ============================================================
    data class UiState(
        val editReminder: EventReminder? = null,
        val errorMessage: String? = null,
        val saved: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    // ============================================================
    // GROUPED UI (Home Screen)
    // ============================================================
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

                if (todayList.isNotEmpty())
                    groups.add(GroupedUiSection("Today", todayList.sortedBy { it.eventEpochMillis }))

                if (tomorrowList.isNotEmpty())
                    groups.add(GroupedUiSection("Tomorrow", tomorrowList.sortedBy { it.eventEpochMillis }))

                if (weekList.isNotEmpty())
                    groups.add(GroupedUiSection("This Week", weekList.sortedBy { it.eventEpochMillis }))

                if (laterList.isNotEmpty())
                    groups.add(GroupedUiSection("Later", laterList.sortedBy { it.eventEpochMillis }))

                groups
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ============================================================
    // LOAD REMINDER
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

    fun resetSaved() { _uiState.value = _uiState.value.copy(saved = false) }
    fun resetError() { _uiState.value = _uiState.value.copy(errorMessage = null) }

    // ============================================================
    // SAVE REMINDER + MULTIPLE ALARMS
    // ============================================================
    fun saveReminder(reminder: EventReminder) = viewModelScope.launch {
        try {
            val isNew = reminder.id == 0L
            val id = if (isNew) repo.insert(reminder) else {
                repo.update(reminder)
                reminder.id
            }

            val saved = repo.getReminder(id)
            if (saved == null) {
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to save")
                return@launch
            }

            val offsets = saved.reminderOffsets.ifEmpty { listOf(0L) }

            val nextEvent = NextOccurrenceCalculator.nextOccurrence(
                saved.eventEpochMillis,
                saved.timeZone,
                saved.repeatRule
            ) ?: saved.eventEpochMillis

            // Clear old alarms
            scheduler.cancelAll(
                reminderId = id,
                offsets = saved.reminderOffsets
            )

            // Schedule new
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

            _uiState.value = _uiState.value.copy(
                saved = true,
                editReminder = saved
            )

        } catch (e: Exception) {
            Timber.e(e)
            _uiState.value = _uiState.value.copy(errorMessage = e.message)
        }
    }

    // ============================================================
    // DELETE REMINDER + CANCEL ALARMS
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
    // Home Screen Bottom Tray
    // ============================================================
    fun cleanupOldReminders() = viewModelScope.launch {
        //repo.cleanupOldOneTimeReminders()
    }
    fun generatePdfReport() = viewModelScope.launch {
        //repo.createPdfReport() // returns file path or uri
    }
    fun exportRemindersCsv() = viewModelScope.launch {
        //repo.createPdfReport() // returns file path or uri
    }
    fun syncRemindersWithServer() = viewModelScope.launch {
        //repo.createPdfReport() // returns file path or uri
    }

}
