package com.example.eventreminder.pdf

import java.time.LocalDateTime
import javax.inject.Inject

/**
 * RealReportBuilder
 *
 * Caller(s):
 *  - PdfViewModel.allAlarmsReport()
 *
 * Responsibility:
 *  - Builds an in-memory report model representing all ACTIVE alarms.
 *  - Loads alarms from the repository (already UID-scoped).
 *  - Groups alarms by event title (A → Z).
 *  - Sorts alarms by next trigger time (earliest first).
 *  - Produces a structured report object for PDF rendering.
 *
 * Guarantees:
 *  - Returned report contains ONLY enabled, upcoming alarms.
 *  - Ordering is deterministic and stable.
 *
 * Side Effects:
 *  - None (pure data construction).
 */
class RealReportBuilder @Inject constructor(
    private val alarmRepo: RealAlarmRepository
) {

    /**
     * Caller:
     *  - PdfViewModel.runTodo3RealReport()
     *
     * Responsibility:
     *  - Builds an in-memory report model representing all ACTIVE alarms.
     *  - Loads alarms from the repository (already UID-scoped).
     *  - Groups alarms by event title (A → Z).
     *  - Sorts alarms by next trigger time (earliest first).
     *  - Produces a structured report object for PDF rendering.
     *
     * Input:
     *  - None (data is loaded from RealAlarmRepository).
     *
     * Output:
     *  - ActiveAlarmReport containing:
     *      - groupedByTitle: alarms grouped and sorted by title
     *      - sortedAlarms: flat list sorted by next trigger time
     *      - generatedAt: current local date-time
     *
     * Guarantees:
     *  - Returned report contains ONLY enabled, upcoming alarms.
     *  - Ordering is deterministic and stable.
     *
     * Side Effects:
     *  - None (pure data construction).
     */
    suspend fun buildReport(): ActiveAlarmReport {

        val alarms = alarmRepo.loadActiveAlarms()

        // Group by title (A→Z)
        val grouped = alarms
            .groupBy { it.eventTitle }
            .toSortedMap()
            .map { (title, list) ->
                TitleSection(
                    title = title,
                    alarms = list.sortedBy { it.nextTrigger }
                )
            }

        val flatSorted = alarms.sortedBy { it.nextTrigger }

        return ActiveAlarmReport(
            groupedByTitle = grouped,
            sortedAlarms = flatSorted,
            generatedAt = LocalDateTime.now()
        )
    }

    // =========================================================
    // NEXT 7 DAYS REMINDERS (FLAT, SORTED)
    // =========================================================
    /**
     * Caller(s):
     *  - PdfViewModel.generateNext7DaysRemindersPdf()
     *
     * Responsibility:
     *  - Loads ACTIVE alarm entries from the repository.
     *  - Filters alarms whose next trigger time lies within the next 7 days.
     *  - Returns a flat list sorted strictly by next trigger time.
     *
     * Output:
     *  - List<AlarmEntry> suitable for flat PDF table rendering.
     *
     * Guarantees:
     *  - Returned list contains ONLY enabled, upcoming alarms.
     *  - No grouping is applied.
     *  - Ordering is deterministic and time-based.
     *
     * Side Effects:
     *  - None (pure data construction).
     */
    suspend fun buildNext7DaysReminders(): List<AlarmEntry> {

        val now = System.currentTimeMillis()
        val sevenDaysLater = now + (7L * 24 * 60 * 60 * 1000)

        val alarms = alarmRepo.loadActiveAlarms()

        return alarms
            .filter { alarm ->
                alarm.nextTrigger in now..sevenDaysLater
            }
            .sortedBy { it.nextTrigger }
    }

}
