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

    val groupedEvents: StateFlow<List<GroupedUiSection>> =
        repo.getAllReminders()
            .map { reminders ->

                val uiList = reminders
                    .filter { it.enabled }
                    .map { rem ->

                        val nextEpoch = NextOccurrenceCalculator.nextOccurrence(
                            rem.eventEpochMillis,
                            rem.timeZone,
                            rem.repeatRule
                        ) ?: rem.eventEpochMillis

                        EventReminderUI.from(
                            id = rem.id,
                            title = rem.title,
                            desc = rem.description,
                            eventMillis = nextEpoch,
                            repeat = rem.repeatRule,
                            tz = rem.timeZone
                        )
                    }
                    .sortedBy { it.eventEpochMillis }

                groupUiEvents(uiList)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
