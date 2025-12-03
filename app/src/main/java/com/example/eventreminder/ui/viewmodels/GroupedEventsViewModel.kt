package com.example.eventreminder.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.util.NextOccurrenceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class GroupedEventsViewModel @Inject constructor(
    private val repo: ReminderRepository
) : ViewModel() {

    // ============================================================
    // 📅 GROUPED EVENTS FOR HOME SCREEN UI
    // Called from HomeScreen to render the grouped reminder list
    // ============================================================
    val groupedEvents: StateFlow<List<GroupedUiSection>> =
        repo.getAllReminders()
            .map { reminders ->

                // ------------------------------------------------------------
                // STEP 1: Filter only enabled reminders
                // ------------------------------------------------------------
                val enabledReminders = reminders.filter { it.enabled }

                // ------------------------------------------------------------
                // STEP 2: Convert each DB reminder → EventReminderUI
                // Also computes next occurrence if repeating
                // ------------------------------------------------------------
                val uiList = enabledReminders.map { rem ->

                    // Resolve the next fire-time for repeating reminders.
                    // If no future repeat exists, fallback to stored epoch.
                    val nextEpoch = NextOccurrenceCalculator.nextOccurrence(
                        rem.eventEpochMillis,
                        rem.timeZone,
                        rem.repeatRule
                    ) ?: rem.eventEpochMillis

                    // Convert into a UI-friendly object for display.
                    EventReminderUI.from(
                        id = rem.id,
                        title = rem.title,
                        desc = rem.description,
                        eventMillis = nextEpoch,
                        repeat = rem.repeatRule,
                        tz = rem.timeZone,
                        offsets = rem.reminderOffsets
                    )
                }
                    // Sort by upcoming time (soonest first)
                    .sortedBy { it.eventEpochMillis }

                // ------------------------------------------------------------
                // STEP 3: Group the converted UI models into:
                // Today, Tomorrow, This Week, Later
                // ------------------------------------------------------------
                groupUiEvents(uiList)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    // --------------------------------------------------------------
    // GROUPING LOGIC
    // --------------------------------------------------------------
    private fun groupUiEvents(list: List<EventReminderUI>): List<GroupedUiSection> {
        if (list.isEmpty()) return emptyList()

        val now = Instant.now().atZone(ZoneId.systemDefault())
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)
        val weekEnd = today.plusDays(7)

        val todayList = mutableListOf<EventReminderUI>()
        val tomorrowList = mutableListOf<EventReminderUI>()
        val weekList = mutableListOf<EventReminderUI>()
        val upcomingList = mutableListOf<EventReminderUI>()
        val pastList = mutableListOf<EventReminderUI>()

        list.forEach { ui ->

            val eventDate = Instant.ofEpochMilli(ui.eventEpochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            when {
                eventDate.isEqual(today) -> todayList.add(ui)
                eventDate.isEqual(tomorrow) -> tomorrowList.add(ui)
                eventDate.isAfter(tomorrow) && eventDate.isBefore(weekEnd) -> weekList.add(ui)
                eventDate.isAfter(weekEnd) -> upcomingList.add(ui)
                else -> pastList.add(ui)
            }
        }

        val result = mutableListOf<GroupedUiSection>()

        if (todayList.isNotEmpty()) result.add(GroupedUiSection("Today", todayList))
        if (tomorrowList.isNotEmpty()) result.add(GroupedUiSection("Tomorrow", tomorrowList))
        if (weekList.isNotEmpty()) result.add(GroupedUiSection("This Week", weekList))
        if (upcomingList.isNotEmpty()) result.add(GroupedUiSection("Upcoming", upcomingList))
        if (pastList.isNotEmpty()) result.add(GroupedUiSection("Past", pastList))

        return result
    }
}
