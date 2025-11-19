package com.example.eventreminder.data.model

enum class ReminderTitle(val label: String, val allowPast: Boolean, val allowFuture: Boolean) {
    BIRTHDAY("Birthday", allowPast = true, allowFuture = false),
    ANNIVERSARY("Anniversary", allowPast = true, allowFuture = false),
    MEETING("Meeting", allowPast = false, allowFuture = true),
    EVENT("Event", allowPast = false, allowFuture = true),
    REMINDER("Reminder", allowPast = false, allowFuture = true),
    PARTY("Party", allowPast = false, allowFuture = true);
}
