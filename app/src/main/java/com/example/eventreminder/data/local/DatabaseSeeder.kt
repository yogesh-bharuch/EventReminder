package com.example.eventreminder.data.local

import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.model.RepeatRule
import com.example.eventreminder.data.model.ReminderOffset
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.min
import java.time.*

@Singleton
class DatabaseSeeder @Inject constructor(
    private val dao: ReminderDao
) {

    private val zone = ZoneId.systemDefault()

    fun seedIfEmpty() {
        CoroutineScope(Dispatchers.IO).launch {
            if (dao.getAllOnce().isNotEmpty()) return@launch
            dao.insertAll(buildSeedData())
        }
    }

    private fun localToEpoch(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        min: Int = 0
    ): Long {
        val dt = LocalDateTime.of(year, month, day, hour, min)
        return dt.atZone(zone).toInstant().toEpochMilli()
    }

    private fun minutesOffset(minutes: Int): Long = minutes * 60_000L

    private fun buildSeedData(): List<EventReminder> {
        val now = LocalDateTime.now(zone)

        return listOf(

            EventReminder(
                title = "Dad’s Birthday",
                eventEpochMillis = localToEpoch(1970, 4, 9, 11, 9),
                timeZone = zone.id,
                repeatRule = RepeatRule.YEARLY.key,
                reminderOffsets = listOf(minutesOffset(1440))
            ),

            EventReminder(
                title = "Wedding Anniversary",
                eventEpochMillis = localToEpoch(now.year, now.monthValue, now.dayOfMonth, 7, 0),
                timeZone = zone.id,
                repeatRule = RepeatRule.YEARLY.key,
                reminderOffsets = listOf(minutesOffset(120))
            ),

            EventReminder(
                title = "Team Meeting",
                eventEpochMillis = localToEpoch(now.year, now.monthValue, now.dayOfMonth, 18, 30),
                timeZone = zone.id,
                repeatRule = null,
                reminderOffsets = listOf(minutesOffset(10), minutesOffset(30))
            ),

            EventReminder(
                title = "Morning Medicine",
                eventEpochMillis = localToEpoch(now.year, now.monthValue, now.dayOfMonth, 9, 0),
                timeZone = zone.id,
                repeatRule = RepeatRule.DAILY.key,
                reminderOffsets = listOf(0L)
            ),

            EventReminder(
                title = "Water Plants",
                eventEpochMillis = localToEpoch(now.year, now.monthValue, now.dayOfMonth, 7, 0),
                timeZone = zone.id,
                repeatRule = RepeatRule.DAILY.key,
                reminderOffsets = listOf(minutesOffset(5), minutesOffset(10))
            ),

            EventReminder(
                title = "Sunday Gym",
                eventEpochMillis = localToEpoch(
                    now.year,
                    now.monthValue,
                    now.dayOfMonth - now.dayOfWeek.value + 7,
                    8,
                    0
                ),
                timeZone = zone.id,
                repeatRule = RepeatRule.WEEKLY.key,
                reminderOffsets = listOf(minutesOffset(60))
            ),

            EventReminder(
                title = "Pay Rent",
                eventEpochMillis = localToEpoch(now.year, 1, 31, 10, 0),
                timeZone = zone.id,
                repeatRule = RepeatRule.MONTHLY.key,
                reminderOffsets = listOf(minutesOffset(1440))
            ),

            EventReminder(
                title = "Car EMI",
                eventEpochMillis = localToEpoch(now.year, now.monthValue, 15, 12, 0),
                timeZone = zone.id,
                repeatRule = RepeatRule.MONTHLY.key,
                reminderOffsets = listOf(minutesOffset(180))
            ),

            EventReminder(
                title = "Ravi Birthday",
                eventEpochMillis = localToEpoch(1995, 10, 20, 0, 0),
                timeZone = zone.id,
                repeatRule = RepeatRule.YEARLY.key,
                reminderOffsets = listOf(minutesOffset(1440))
            ),

            EventReminder(
                title = "Cook Dinner",
                eventEpochMillis = localToEpoch(
                    now.plusDays(1).year,
                    now.plusDays(1).monthValue,
                    now.plusDays(1).dayOfMonth,
                    17,
                    30
                ),
                timeZone = zone.id,
                repeatRule = null,
                reminderOffsets = listOf(minutesOffset(15), minutesOffset(60))
            ),

            EventReminder(
                title = "Flight to Mumbai",
                eventEpochMillis = localToEpoch(
                    now.plusDays(3).year,
                    now.plusDays(3).monthValue,
                    now.plusDays(3).dayOfMonth,
                    4,
                    45
                ),
                timeZone = zone.id,
                repeatRule = null,
                reminderOffsets = listOf(minutesOffset(1440), minutesOffset(180), minutesOffset(60))
            ),

            EventReminder(
                title = "Quarterly Townhall",
                eventEpochMillis = localToEpoch(
                    now.plusMonths(1).year,
                    now.plusMonths(1).monthValue,
                    now.plusMonths(1).dayOfMonth,
                    11,
                    0
                ),
                timeZone = zone.id,
                reminderOffsets = listOf(minutesOffset(1440))
            ),

            EventReminder(
                title = "Diwali",
                eventEpochMillis = localToEpoch(2024, 11, 12, 0, 0),
                timeZone = zone.id,
                repeatRule = RepeatRule.YEARLY.key,
                reminderOffsets = listOf(minutesOffset(2880))
            ),

            EventReminder(
                title = "Medicine Refill",
                eventEpochMillis = localToEpoch(
                    now.minusDays(7).year,
                    now.minusDays(7).monthValue,
                    now.minusDays(7).dayOfMonth,
                    8,
                    0
                ),
                timeZone = zone.id,
                repeatRule = RepeatRule.WEEKLY.key,
                reminderOffsets = listOf(minutesOffset(1440))
            ),

            EventReminder(
                title = "Netflix Renewal",
                eventEpochMillis = localToEpoch(
                    now.plusMonths(1).year,
                    now.plusMonths(1).monthValue,
                    1,
                    10,
                    0
                ),
                timeZone = zone.id,
                repeatRule = RepeatRule.MONTHLY.key,
                reminderOffsets = listOf(minutesOffset(4320))
            ),

            EventReminder(
                title = "Debug Event",
                eventEpochMillis = System.currentTimeMillis(),
                timeZone = zone.id,
                repeatRule = RepeatRule.EVERY_MINUTE.key,
                reminderOffsets = listOf(0L)
            ),

            EventReminder(
                title = "Weekend Trip",
                eventEpochMillis = localToEpoch(
                    now.plusDays(2).year,
                    now.plusDays(2).monthValue,
                    now.plusDays(2).dayOfMonth,
                    18,
                    0
                ),
                timeZone = zone.id,
                reminderOffsets = listOf(minutesOffset(180))
            ),

            EventReminder(
                title = "Doctor Appointment",
                eventEpochMillis = localToEpoch(
                    now.plusDays(10).year,
                    now.plusDays(10).monthValue,
                    now.plusDays(10).dayOfMonth,
                    10,
                    30
                ),
                timeZone = zone.id,
                reminderOffsets = listOf(minutesOffset(1440))
            ),

            EventReminder(
                title = "Insurance Expiry",
                eventEpochMillis = localToEpoch(
                    now.plusMonths(6).year,
                    now.plusMonths(6).monthValue,
                    now.plusMonths(6).dayOfMonth,
                    9,
                    0
                ),
                timeZone = zone.id,
                repeatRule = RepeatRule.YEARLY.key,
                reminderOffsets = listOf(minutesOffset(10080))
            ),

            EventReminder(
                title = "Solar Panel Cleaning",
                eventEpochMillis = localToEpoch(
                    now.year,
                    now.monthValue,
                    min(30, now.month.length(now.toLocalDate().isLeapYear))
                ),
                timeZone = zone.id,
                repeatRule = RepeatRule.MONTHLY.key,
                reminderOffsets = listOf(minutesOffset(1440))
            ),

            EventReminder(
                title = "Loan Payment",
                eventEpochMillis = localToEpoch(2020, 1, 1, 9, 0),
                timeZone = zone.id,
                repeatRule = RepeatRule.MONTHLY.key,
                reminderOffsets = listOf(minutesOffset(1440))
            ),

            EventReminder(
                title = "Study Time",
                eventEpochMillis = localToEpoch(now.year, now.monthValue, now.dayOfMonth, 20, 0),
                timeZone = zone.id,
                reminderOffsets = listOf(minutesOffset(10))
            ),

            EventReminder(
                title = "Evening Medicine",
                eventEpochMillis = localToEpoch(now.year, now.monthValue, now.dayOfMonth, 19, 0),
                timeZone = zone.id,
                repeatRule = RepeatRule.DAILY.key,
                reminderOffsets = listOf(minutesOffset(30))
            ),

            EventReminder(
                title = "Project Deadline",
                eventEpochMillis = localToEpoch(
                    now.plusMonths(2).year,
                    now.plusMonths(2).monthValue,
                    now.plusMonths(2).dayOfMonth,
                    23,
                    59
                ),
                timeZone = zone.id,
                reminderOffsets = listOf(minutesOffset(10080))
            ),

            EventReminder(
                title = "Visa Expiry",
                eventEpochMillis = localToEpoch(
                    now.minusDays(5).year,
                    now.minusDays(5).monthValue,
                    now.minusDays(5).dayOfMonth,
                    0,
                    0
                ),
                timeZone = zone.id,
                repeatRule = RepeatRule.YEARLY.key,
                reminderOffsets = listOf(minutesOffset(2880))
            )
        )
    }
}
