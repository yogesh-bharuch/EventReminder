package com.example.eventreminder.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.util.NextOccurrenceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import javax.inject.Inject

// ===============================================================
// TIME CONSTANTS
// ===============================================================
private const val DAY_MILLIS = 86_400_000L
private const val WEEK_DAYS = 7L
private const val MONTH_DAYS = 30L

@HiltViewModel
class GroupedEventsViewModel @Inject constructor(
    private val repo: ReminderRepository
) : ViewModel() {

    // ============================================================
    // 📅 GROUPED EVENTS — consumed in HomeScreen → EventsListGrouped
    // ============================================================
    val groupedEvents: StateFlow<List<GroupedUiSection>> =
        repo.getAllReminders()
            .map { reminders ->

                // ------------------------------------------------------------
                // STEP 1: Filter enabled reminders only
                // ------------------------------------------------------------
                val enabled = reminders.filter { it.enabled }

                // ------------------------------------------------------------
                // STEP 2: Convert DB → UI model w/ Next Occurrence
                // ------------------------------------------------------------
                val uiList = enabled.map { rem ->

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
                        tz = rem.timeZone,
                        offsets = rem.reminderOffsets
                    )
                }
                    .sortedBy { it.eventEpochMillis }

                // ------------------------------------------------------------
                // STEP 3: Apply advanced grouping
                // ------------------------------------------------------------
                groupUiEvents(uiList)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    // ============================================================
    // NEW ADVANCED GROUPING (Today / Tomorrow / Next7 / Next30 /
    // Upcoming / Past7 / Archives)
    // ============================================================
    private fun groupUiEvents(list: List<EventReminderUI>): List<GroupedUiSection> {
        if (list.isEmpty()) return emptyList()

        // Thresholds
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowStart = todayStart + DAY_MILLIS
        val next7Start = todayStart + (2 * DAY_MILLIS)
        val weekEnd = todayStart + (WEEK_DAYS * DAY_MILLIS)
        val monthEnd = todayStart + (MONTH_DAYS * DAY_MILLIS)
        val past7Threshold = now - (WEEK_DAYS * DAY_MILLIS)

        // Groups
        val todayList = mutableListOf<EventReminderUI>()
        val tomorrowList = mutableListOf<EventReminderUI>()
        val next7 = mutableListOf<EventReminderUI>()
        val next30 = mutableListOf<EventReminderUI>()
        val upcoming = mutableListOf<EventReminderUI>()
        val past7 = mutableListOf<EventReminderUI>()
        val archives = mutableListOf<EventReminderUI>()

        // ------------------------------------------------------------
        // CLASSIFY EACH REMINDER
        // ------------------------------------------------------------
        list.forEach { ui ->

            val t = ui.eventEpochMillis

            when {
                // ------------------ FUTURE ------------------
                t in todayStart until tomorrowStart ->
                    todayList.add(ui)

                t in tomorrowStart until (tomorrowStart + DAY_MILLIS) ->
                    tomorrowList.add(ui)

                t in next7Start until weekEnd ->
                    next7.add(ui)

                t in weekEnd until monthEnd ->
                    next30.add(ui)

                t >= monthEnd ->
                    upcoming.add(ui)

                // ------------------ PAST --------------------
                t in past7Threshold until now ->
                    past7.add(ui)

                else ->
                    archives.add(ui)
            }
        }

        // ------------------------------------------------------------
        // BUILD SECTIONS (only non-empty)
        // ------------------------------------------------------------
        val sections = mutableListOf<GroupedUiSection>()

        fun add(header: String, items: List<EventReminderUI>, sortDesc: Boolean = false) {
            if (items.isNotEmpty()) {
                sections.add(
                    GroupedUiSection(
                        header = header,
                        events = if (sortDesc)
                            items.sortedByDescending { it.eventEpochMillis }
                        else
                            items.sortedBy { it.eventEpochMillis }
                    )
                )
            }
        }

        // Future
        add("Today", todayList)
        add("Tomorrow", tomorrowList)
        add("Next 7 Days", next7)
        add("Next 30 Days", next30)
        add("Upcoming", upcoming)

        // Past
        add("Past 7 Days", past7, sortDesc = true)
        add("Archives", archives, sortDesc = true)

        return sections
    }
}
