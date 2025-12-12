package com.example.eventreminder.ui.notification

import android.content.Context
import android.net.Uri
import com.example.eventreminder.R
import timber.log.Timber
import androidx.core.net.toUri

/**
 * Category-based auto ringtone selector with Timber logging.
 */
object RingtoneResolver {

    private const val TAG = "RingtoneResolver"

    fun resolve(context: Context, title: String?, message: String?): Uri? {
        val raw = "${title.orEmpty()} ${message.orEmpty()}".lowercase()

        Timber.tag(TAG).d("Resolving ringtone for text=\"$raw\"")
        // ⭐ LOG HERE — this MUST print for every incoming alarm
        Timber.tag(TAG)
            .e("resolve() invoked → text='$raw' title='$title' message='$message'")

        return when {
            containsAny(raw, listOf("birthday", "bday")) -> {
                val uri = rawUri(context, R.raw.birthday)
                Timber.tag(TAG).d("Matched: BIRTHDAY → $uri")
                uri
            }

            containsAny(raw, listOf("anniversary")) -> {
                val uri = rawUri(context, R.raw.anniversary)
                Timber.tag(TAG).d("Matched: ANNIVERSARY → $uri")
                uri
            }

            containsAny(raw, listOf("medicine", "pill", "tablet")) -> {
                val uri = rawUri(context, R.raw.medicine)
                Timber.tag(TAG).d("Matched: MEDICINE → $uri")
                uri
            }

            containsAny(raw, listOf("meeting", "call", "interview")) -> {
                val uri = rawUri(context, R.raw.meeting)
                Timber.tag(TAG).d("Matched: MEETING → $uri")
                uri
            }

            containsAny(raw, listOf("gym", "workout", "exercise")) -> {
                val uri = rawUri(context, R.raw.workout)
                Timber.tag(TAG).d("Matched: WORKOUT → $uri")
                uri
            }

            else -> {
                Timber.tag(TAG).d("No match → Using DEFAULT system sound")
                null
            }
        }
    }

    private fun rawUri(context: Context, resId: Int): Uri =
        "android.resource://${context.packageName}/$resId".toUri()
            .also { Timber.tag(TAG).d("Generated raw URI: $it") }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { key ->
            text.contains(key).also {
                if (it) Timber.tag(TAG).d("containsAny → matched keyword \"$key\"")
            }
        }
    }
}



