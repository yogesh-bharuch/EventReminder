package com.example.eventreminder.pdf

import com.example.eventreminder.logging.DEBUG_TAG
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * ReminderReportDataBuilder
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
class ReminderReportDataBuilder @Inject constructor(
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
    suspend fun buildActiveAlarmReport(): ActiveAlarmReport {

        val zoneId = ZoneId.systemDefault()
        val now = Instant.now()

        Timber.tag(DEBUG_TAG).d(
            "BUILD_ACTIVE_REPORT_START now=${now.atZone(zoneId)} epoch=${now.toEpochMilli()} " +
                    "[ReminderReportDataBuilder.kt::buildActiveAlarmReport]"
        )

        val alarms = alarmRepo.loadActiveAlarms()

        Timber.tag(DEBUG_TAG).d(
            "ACTIVE_ALARMS_LOADED count=${alarms.size} " +
                    "[ReminderReportDataBuilder.kt::buildActiveAlarmReport]"
        )

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

        val report = ActiveAlarmReport(
            groupedByTitle = grouped,
            sortedAlarms = flatSorted,
            generatedAt = LocalDateTime.now()
        )

        Timber.tag(DEBUG_TAG).d(
            "BUILD_ACTIVE_REPORT_DONE grouped=${grouped.size} flat=${flatSorted.size} " +
                    "[ReminderReportDataBuilder.kt::buildActiveAlarmReport]"
        )

        return report
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

        val zoneId = ZoneId.systemDefault()
        val todayStart = LocalDate.now(zoneId)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        val sevenDaysLater = todayStart + (7L * 24 * 60 * 60 * 1000)

        Timber.tag(DEBUG_TAG).d(
            "NEXT_7_DAYS_START todayStart=${Instant.ofEpochMilli(todayStart).atZone(zoneId)} " +
                    "limit=${Instant.ofEpochMilli(sevenDaysLater).atZone(zoneId)} " +
                    "[ReminderReportDataBuilder.kt::buildNext7DaysReminders]"
        )

        val alarms = alarmRepo.loadActiveAlarms(includeTodayAlarms = true)

        Timber.tag(DEBUG_TAG).d(
            "ACTIVE_ALARMS_LOADED total=${alarms.size} " +
                    "[ReminderReportDataBuilder.kt::buildNext7DaysReminders]"
        )

        val filtered = alarms
            .filter { alarm ->
                alarm.nextTrigger in todayStart until sevenDaysLater
            }
            .sortedBy { it.nextTrigger }

        Timber.tag(DEBUG_TAG).d(
            "NEXT_7_DAYS_DONE kept=${filtered.size} " +
                    "[ReminderReportDataBuilder.kt::buildNext7DaysReminders]"
        )

        return filtered
    }

}
