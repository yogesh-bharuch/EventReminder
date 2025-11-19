package com.example.eventreminder.pdf

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object ReportFakeData {

    fun generateFakeReport(): ActiveAlarmReport {

        val nowMs = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        fun minutesFromNow(min: Long): Long =
            nowMs + (min * 60_000)

        val alarms = listOf(
            AlarmEntry(
                eventId = 1,
                eventTitle = "Water Plants",
                nextTrigger = minutesFromNow(30),   // 30 min later (epoch)
                offsetMinutes = 30
            ),
            AlarmEntry(
                eventId = 2,
                eventTitle = "Workout",
                nextTrigger = minutesFromNow(120),  // 2 hours later
                offsetMinutes = 60
            ),
            AlarmEntry(
                eventId = 3,
                eventTitle = "Medicine",
                nextTrigger = minutesFromNow(10),   // 10 min later
                offsetMinutes = 10
            ),
            AlarmEntry(
                eventId = 4,
                eventTitle = "Dinner Prep",
                nextTrigger = minutesFromNow(300),  // 5 hours later
                offsetMinutes = 120
            ),
        )

        val grouped = listOf(
            TitleSection(
                "Daily Routine",
                alarms.filter { it.eventTitle in listOf("Water Plants", "Workout") }
            ),
            TitleSection(
                "Health",
                alarms.filter { it.eventTitle == "Medicine" }
            ),
            TitleSection(
                "Food",
                alarms.filter { it.eventTitle == "Dinner Prep" }
            ),
        )

        return ActiveAlarmReport(
            groupedByTitle = grouped,
            sortedAlarms = alarms.sortedBy { it.nextTrigger },
            generatedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
        )
    }
}
