package com.example.eventreminder.pdf

import java.time.LocalDateTime
import javax.inject.Inject

class RealReportBuilder @Inject constructor(
    private val alarmRepo: RealAlarmRepository
) {

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
