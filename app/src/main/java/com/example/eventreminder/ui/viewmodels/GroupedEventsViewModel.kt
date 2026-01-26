package com.example.eventreminder.ui.viewmodels

// =============================================================
// Imports
// =============================================================
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.util.NextOccurrenceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

// ===============================================================
// TIME CONSTANTS
// ===============================================================
private const val DAY_MILLIS = 86_400_000L
private const val WEEK_DAYS = 7L
private const val MONTH_DAYS = 30L
private const val PAST_GRACE_DAYS = 30L
private const val UI_TICK_MILLIS = 5_000L // 1 minute UI recompute

// ===============================================================
// HELPERS
// ===============================================================
private fun EventReminder.isOneTime(): Boolean =
    this.repeatRule.isNullOrBlank()

// ===============================================================
// ViewModel
// ===============================================================
@HiltViewModel
class GroupedEventsViewModel @Inject constructor(
    private val repo: ReminderRepository
) : ViewModel() {

    // ============================================================
    // ⏱️ UI CLOCK — forces regrouping after repeat fires
    // ============================================================
    private val nowFlow: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(UI_TICK_MILLIS)
        }
    }

    // ============================================================
    // 📅 GROUPED EVENTS — HomeScreen
    // ============================================================
    val groupedEvents: StateFlow<List<GroupedUiSection>> =
        combine(
            repo.getAllReminders(),
            nowFlow
        ) { reminders, now ->

            // ----------------------------------------------------
            // VISIBILITY RULE (STATE FIRST)
            // ----------------------------------------------------
            // Enabled reminders → always visible
            // Disabled one-time reminders → past grace only
            val visible = reminders.filter { rem ->
                when {
                    rem.enabled -> true
                    rem.isOneTime() -> isWithinPastGrace(rem, now)
                    else -> false
                }
            }

            // ----------------------------------------------------
            // DB → UI MODEL (NEXT OCCURRENCE DRIVEN)
            // ----------------------------------------------------
            val uiList = visible.map { rem ->

                val nextEpoch =
                    NextOccurrenceCalculator.nextOccurrence(
                        eventEpochMillis = rem.eventEpochMillis,
                        zoneIdStr = rem.timeZone,
                        repeatRule = rem.repeatRule
                    )

                EventReminderUI.from(
                    id = rem.id,
                    title = rem.title,
                    desc = rem.description,
                    // 🔑 repeating → next occurrence
                    // 🔑 one-time → original event time
                    eventMillis = nextEpoch ?: rem.eventEpochMillis,
                    repeat = rem.repeatRule,
                    tz = rem.timeZone,
                    offsets = rem.reminderOffsets
                )
            }

            groupUiEvents(
                source = visible,
                uiList = uiList,
                now = now
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyList()
            )

    // ============================================================
    // 30-DAY PAST VISIBILITY WINDOW (ONE-TIME ONLY)
    // ============================================================
    private fun isWithinPastGrace(
        reminder: EventReminder,
        now: Long
    ): Boolean {
        val graceMillis = PAST_GRACE_DAYS * DAY_MILLIS
        return reminder.eventEpochMillis in (now - graceMillis) until now
    }

    // ============================================================
    // GROUPING ENGINE (STATE FIRST, TIME SECOND)
    // ============================================================
    private fun groupUiEvents(
        source: List<EventReminder>,
        uiList: List<EventReminderUI>,
        now: Long
    ): List<GroupedUiSection> {

        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()

        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowStart = todayStart + DAY_MILLIS
        val next7Start = todayStart + (2 * DAY_MILLIS)
        val weekEnd = todayStart + (WEEK_DAYS * DAY_MILLIS)
        val monthEnd = todayStart + (MONTH_DAYS * DAY_MILLIS)

        val todayList = mutableListOf<EventReminderUI>()
        val tomorrowList = mutableListOf<EventReminderUI>()
        val next7 = mutableListOf<EventReminderUI>()
        val next30 = mutableListOf<EventReminderUI>()
        val upcoming = mutableListOf<EventReminderUI>()
        val past30 = mutableListOf<EventReminderUI>()

        uiList.forEachIndexed { index, ui ->
            val original = source[index]

            // =====================================================
            // ⭐ SINGLE SOURCE OF TRUTH
            // One-time reminder that has fired → disabled → past
            // =====================================================
            val isExpiredOneTime =
                original.isOneTime() && !original.enabled

            if (isExpiredOneTime) {
                past30.add(ui)
                return@forEachIndexed
            }

            // -----------------------------------------------------
            // NORMAL TIME-BASED GROUPING (NEXT OCCURRENCE)
            // -----------------------------------------------------
            when {
                ui.eventEpochMillis in todayStart until tomorrowStart ->
                    todayList.add(ui)

                ui.eventEpochMillis in tomorrowStart until (tomorrowStart + DAY_MILLIS) ->
                    tomorrowList.add(ui)

                ui.eventEpochMillis in next7Start until weekEnd ->
                    next7.add(ui)

                ui.eventEpochMillis in weekEnd until monthEnd ->
                    next30.add(ui)

                ui.eventEpochMillis >= monthEnd ->
                    upcoming.add(ui)
            }
        }

        // ---------------------------------------------------------
        // SECTION BUILDER
        // ---------------------------------------------------------
        fun section(
            title: String,
            items: List<EventReminderUI>,
            desc: Boolean = false
        ): GroupedUiSection? =
            items.takeIf { it.isNotEmpty() }?.let {
                GroupedUiSection(
                    header = title,
                    events = if (desc)
                        it.sortedByDescending { e -> e.eventEpochMillis }
                    else
                        it.sortedBy { e -> e.eventEpochMillis }
                )
            }

        return listOfNotNull(
            section("Today", todayList),
            section("Tomorrow", tomorrowList),
            section("Next 7 Days", next7),
            section("Next 30 Days", next30),
            section("Upcoming", upcoming),
            section("Past 30 Days", past30, desc = true)
        )
    }
}
