package com.example.eventreminder.data.local

// =============================================================
// DatabaseSeeder (UID-scoped, optional)
// =============================================================

import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.model.RepeatRule
import com.example.eventreminder.sync.core.UserIdProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.*
import kotlin.math.min

@Singleton
class DatabaseSeeder @Inject constructor(
    private val dao: ReminderDao,
    private val userIdProvider: UserIdProvider
) {

    private val zone = ZoneId.systemDefault()

    fun seedIfEmpty() {
        CoroutineScope(Dispatchers.IO).launch {

            val uid = userIdProvider.getUserId()
                ?: return@launch // user not logged in → do nothing

            if (dao.getAllOnce(uid).isNotEmpty()) return@launch

            dao.insertAll(buildSeedData(uid))
        }
    }

    private fun localToEpoch(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        min: Int = 0
    ): Long =
        LocalDateTime.of(year, month, day, hour, min)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun minutesOffset(minutes: Int): Long =
        minutes * 60_000L

    private fun buildSeedData(uid: String): List<EventReminder> {
        val now = LocalDateTime.now(zone)

        return listOf(
            EventReminder(
                uid = uid,
                title = "Dad’s Birthday",
                eventEpochMillis = localToEpoch(1970, 4, 9, 11, 9),
                timeZone = zone.id,
                repeatRule = RepeatRule.YEARLY.key,
                reminderOffsets = listOf(minutesOffset(1440))
            ),

            EventReminder(
                uid = uid,
                title = "Team Meeting",
                eventEpochMillis = localToEpoch(
                    now.year,
                    now.monthValue,
                    now.dayOfMonth,
                    18,
                    30
                ),
                timeZone = zone.id,
                reminderOffsets = listOf(minutesOffset(10))
            )
            // keep only minimal demo entries if needed
        )
    }
}
