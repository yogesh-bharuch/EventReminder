package com.example.eventreminder.pdf

import java.time.LocalDateTime
import javax.inject.Inject

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
}
