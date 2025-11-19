package com.example.eventreminder.pdf

import java.time.LocalDateTime

object ReportFakeData {

    fun generateFakeReport(): ActiveAlarmReport {

        val now = LocalDateTime.now()

        val alarms = listOf(
            AlarmEntry(1, "Water Plants", now.plusMinutes(30), -30),
            AlarmEntry(2, "Workout", now.plusHours(2), -60),
            AlarmEntry(3, "Medicine", now.plusMinutes(10), -10),
            AlarmEntry(4, "Dinner Prep", now.plusHours(5), -120),
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
            generatedAt = now
        )
    }
}
