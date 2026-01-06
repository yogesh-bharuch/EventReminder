package com.example.eventreminder.data.model

enum class ReminderOffset(val millis: Long, val label: String) {
    AT_TIME(0L, "At time"),
    MIN_5(5 * 60_000L, "5 minutes before"),
    MIN_10(10 * 60_000L, "10 minutes before"),
    MIN_30(30 * 60_000L, "30 minutes before"),
    HOUR_1(60 * 60_000L, "1 hour before"),
    HOUR_3(3 * 60 * 60_000L, "3 hours before"),
    DAY_1(24 * 60 * 60_000L, "1 day before"),
    MONTH_1(30L * 24 * 60 * 60_000L, "30 days before");

    override fun toString(): String = label

    companion object {
        fun fromMillis(m: Long): ReminderOffset =
            entries.find { it.millis == m } ?: AT_TIME
    }
}
