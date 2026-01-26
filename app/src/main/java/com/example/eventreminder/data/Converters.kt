package com.example.eventreminder.data

import androidx.room.TypeConverter
import timber.log.Timber

/**
 * Converters.kt
 *
 * Room TypeConverters for converting List<Long> <-> CSV String.
 *
 * Notes:
 * - Uses CSV ("0,3600000,86400000") because your existing code used that format.
 * - This implementation is defensive: returns a safe default list if parsing fails.
 */

private const val TAG = "Converters"

class Converters {

    // Convert List<Long> to CSV string for storage
    @TypeConverter
    fun fromLongList(list: List<Long>?): String {
        return try {
            if (list.isNullOrEmpty()) {
                // Persist at least a single 0 so that consumer code expecting a single offset behaves
                "0"
            } else {
                list.joinToString(separator = ",") { it.toString() }
            }
        } catch (ex: Exception) {
            Timber.tag(TAG).e(ex, "fromLongList - serialization error, returning default '0'")
            "0"
        }
    }

    // Convert CSV string back to List<Long>
    @TypeConverter
    fun toLongList(data: String?): List<Long> {
        if (data.isNullOrBlank()) {
            return listOf(0L)
        }

        return try {
            // split by comma and parse longs; ignore empty tokens
            data.split(",")
                .mapNotNull { token ->
                    val trimmed = token.trim()
                    if (trimmed.isEmpty()) null else trimmed.toLongOrNull()
                }
                .ifEmpty { listOf(0L) } // ensure at least one offset
        } catch (ex: Exception) {
            Timber.tag(TAG).e(ex, "toLongList - parse error for '$data' - returning default [0]")
            listOf(0L)
        }
    }
}
