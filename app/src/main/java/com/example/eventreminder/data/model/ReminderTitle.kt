package com.example.eventreminder.data.model

import androidx.annotation.RawRes
import com.example.eventreminder.R

enum class ReminderTitle(val label: String, val allowPast: Boolean, val allowFuture: Boolean, @RawRes val soundResId: Int) {
    BIRTHDAY("Birthday", true, false, R.raw.birthday),
    ANNIVERSARY("Anniversary", true, false, R.raw.anniversary),

    MEDICINE("Medicine", false, true, R.raw.medicine),
    WORKOUT("Workout", false, true, R.raw.workout),

    MEETING("Meeting", false, true, R.raw.meeting),
    EVENT("Event", false, true, R.raw.meeting),
    REMINDER("Reminder", false, true, R.raw.meeting),
    PARTY("Party", false, true, R.raw.meeting);
}
