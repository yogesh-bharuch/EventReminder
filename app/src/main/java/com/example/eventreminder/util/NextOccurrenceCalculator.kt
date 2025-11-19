package com.example.eventreminder.util

import java.time.*
import java.time.temporal.TemporalAdjusters
import kotlin.math.min

/**
 * Computes the next occurrence (epoch millis, UTC) for a reminder based on repeatRule.
 *
 * Inputs:
 *  - eventEpochMillis: Stored instant (UTC) for the event's LOCAL wall-clock time.
 *  - zoneIdStr: Original IANA zone (e.g., "Asia/Kolkata").
 *  - repeatRule: null | "every_minute" | "daily" | "weekly" | "monthly" | "yearly"
 *
 * Output:
 *  - Epoch millis (UTC) of next event, or null if one-time event already passed.
 *
 * Logic:
 *  - Convert event time to ZonedDateTime in original zone.
 *  - Compute next occurrence using local date/time rules.
 *  - Convert back to UTC millis.
 */
object NextOccurrenceCalculator {

    fun nextOccurrence(
        eventEpochMillis: Long,
        zoneIdStr: String,
        repeatRule: String?
    ): Long? {

        val zone = try {
            ZoneId.of(zoneIdStr)
        } catch (e: Exception) {
            ZoneId.systemDefault()
        }

        val eventZdt = Instant.ofEpochMilli(eventEpochMillis).atZone(zone)
        val nowZdt = ZonedDateTime.now(zone)

        return when (repeatRule) {

            // ------------------------------------------------------
            // ONE-TIME EVENT
            // ------------------------------------------------------
            null -> {
                val eventInstant = eventZdt.toInstant()
                if (eventInstant.isAfter(Instant.now())) {
                    eventInstant.toEpochMilli()
                } else null
            }

            // ------------------------------------------------------
            // EVERY MINUTE — Debug/testing mode
            // ------------------------------------------------------
            "every_minute" -> {
                // Start from the original event local-time rounded to second=0
                var candidate = eventZdt.withSecond(0).withNano(0)

                // If candidate <= now, advance by minutes until strictly after now.
                while (!candidate.toInstant().isAfter(nowZdt.toInstant())) {
                    candidate = candidate.plusMinutes(1)
                }
                candidate.toInstant().toEpochMilli()
            }

            // ------------------------------------------------------
            // DAILY
            // ------------------------------------------------------
            "daily" -> {
                var candidate = nowZdt.withHour(eventZdt.hour)
                    .withMinute(eventZdt.minute)
                    .withSecond(eventZdt.second)
                    .withNano(eventZdt.nano)

                if (!candidate.toInstant().isAfter(nowZdt.toInstant())) {
                    candidate = candidate.plusDays(1)
                }
                candidate.toInstant().toEpochMilli()
            }

            // ------------------------------------------------------
            // WEEKLY
            // ------------------------------------------------------
            "weekly" -> {
                val originalTime = eventZdt.toLocalTime()
                val originalDay = eventZdt.dayOfWeek

                val today = nowZdt.toLocalDate()

                // Build candidate for this week's occurrence
                val candidateDate = today.with(TemporalAdjusters.nextOrSame(originalDay))

                var candidate = ZonedDateTime.of(candidateDate, originalTime, zone)

                // If not strictly in the future, move to next week
                if (!candidate.toInstant().isAfter(nowZdt.toInstant())) {
                    val nextDate = candidateDate.plusWeeks(1)
                    candidate = ZonedDateTime.of(nextDate, originalTime, zone)
                }

                candidate.toInstant().toEpochMilli()
            }

            // ------------------------------------------------------
            // MONTHLY (clamps 29/30/31)
            // ------------------------------------------------------
            "monthly" -> {
                val originalDate = eventZdt.toLocalDate()
                val originalTime = eventZdt.toLocalTime()

                val today = nowZdt.toLocalDate()

                // --- Build this month's candidate ---
                val year = today.year
                val month = today.month
                val maxDay = YearMonth.of(year, month).lengthOfMonth()
                val safeDay = min(originalDate.dayOfMonth, maxDay)

                var candidateDate = LocalDate.of(year, month, safeDay)
                var candidate = ZonedDateTime.of(candidateDate, originalTime, zone)

                // If not strictly in the future → next month
                if (!candidate.toInstant().isAfter(nowZdt.toInstant())) {
                    val nextYearMonth = YearMonth.from(today).plusMonths(1)
                    val nextMax = nextYearMonth.lengthOfMonth()
                    val nextSafe = min(originalDate.dayOfMonth, nextMax)

                    candidateDate = LocalDate.of(nextYearMonth.year, nextYearMonth.month, nextSafe)
                    candidate = ZonedDateTime.of(candidateDate, originalTime, zone)
                }

                candidate.toInstant().toEpochMilli()
            }

            // ------------------------------------------------------
            // YEARLY (handles Feb 29)
            // ------------------------------------------------------
            "yearly" -> {
                val originalDate = eventZdt.toLocalDate()
                val originalTime = eventZdt.toLocalTime()

                val today = nowZdt.toLocalDate()

                // Build this year's candidate
                var candidateDate = LocalDate.of(
                    today.year,
                    originalDate.month,
                    min(originalDate.dayOfMonth, YearMonth.of(today.year, originalDate.month).lengthOfMonth())
                )

                var candidate = ZonedDateTime.of(candidateDate, originalTime, zone)

                // If candidate is not strictly in the future, advance by 1 year
                if (!candidate.toInstant().isAfter(nowZdt.toInstant())) {
                    val nextYear = today.year + 1
                    val safeDay = min(
                        originalDate.dayOfMonth,
                        YearMonth.of(nextYear, originalDate.month).lengthOfMonth()
                    )

                    candidate = ZonedDateTime.of(
                        LocalDate.of(nextYear, originalDate.month, safeDay),
                        originalTime,
                        zone
                    )
                }

                candidate.toInstant().toEpochMilli()
            }

            // ------------------------------------------------------
            // Unknown rule → treat as one-time
            // ------------------------------------------------------
            else -> {
                val inst = eventZdt.toInstant()
                if (inst.isAfter(Instant.now())) inst.toEpochMilli() else null
            }
        }
    }
}
