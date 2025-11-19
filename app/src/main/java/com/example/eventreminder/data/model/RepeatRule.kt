package com.example.eventreminder.data.model

enum class RepeatRule(val key: String, val label: String) {
    NONE("", "One-time"),
    EVERY_MINUTE("every_minute", "Every Minute"),
    DAILY("daily", "Daily"),
    WEEKLY("weekly", "Weekly"),
    MONTHLY("monthly", "Monthly"),
    YEARLY("yearly", "Yearly");

    override fun toString(): String = label

    companion object {
        fun fromKey(key: String?): RepeatRule =
            values().find { it.key == key } ?: NONE
    }
}
