package com.example.eventreminder.data


import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromLongList(list: List<Long>): String =
        list.joinToString(",")

    @TypeConverter
    fun toLongList(data: String): List<Long> =
        if (data.isBlank()) emptyList()
        else data.split(",").map { it.toLong() }
}
