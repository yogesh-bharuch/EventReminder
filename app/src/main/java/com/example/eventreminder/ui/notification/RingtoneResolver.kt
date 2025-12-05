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



/*
package com.example.eventreminder.ui.notification

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.example.eventreminder.R
import timber.log.Timber

/**
 * Category-based auto ringtone selector using sealed classes.
 */
object RingtoneResolver {

    private const val TAG = "RingtoneResolver"

    // ---------------------------------------------------------
    // 🔊 CATEGORY DEFINITIONS
    // ---------------------------------------------------------
    sealed class RingtoneCategory(
        val keywords: List<String>,
        val soundRes: Int
    ) {
        object Birthday : RingtoneCategory(keywords = listOf("birthday", "bday"), soundRes = R.raw.birthday)
        object Anniversary : RingtoneCategory(keywords = listOf("anniversary", "marriage"), soundRes = R.raw.anniversary)
        object Medicine : RingtoneCategory(keywords = listOf("medicine", "pill", "tablet"), soundRes = R.raw.medicine)
        object Meeting : RingtoneCategory(keywords = listOf("meeting", "call", "interview"), soundRes = R.raw.meeting)
        object Workout : RingtoneCategory(keywords = listOf("gym", "workout", "exercise", "jog"), soundRes = R.raw.workout)
        object None : RingtoneCategory(keywords = emptyList(), soundRes = -1)  // default

        companion object {
            val all = listOf(Birthday, Anniversary, Medicine, Meeting, Workout)
        }
    }

    // ---------------------------------------------------------
    // 🔍 MAIN RESOLVER (called from NotificationHelper)
    // ---------------------------------------------------------
    fun resolve(context: Context, title: String?, message: String?): Uri? {
        val raw = "${title.orEmpty()} ${message.orEmpty()}".lowercase()

        Timber.tag(TAG).e("resolve() invoked → text='$raw'")

        // 1) Find first matching category
        val matched = RingtoneCategory.all.firstOrNull { category ->
            category.keywords.any { raw.contains(it) }
        }

        if (matched != null) {
            //Timber.tag(TAG).d("Matched category: ${matched::class.simpleName}")

            val uri = rawUri(context, matched.soundRes)
            //Timber.tag(TAG).d("Resolved URI → $uri")

            return uri
        }

        Timber.tag(TAG).d("No category matched → using DEFAULT notification sound")
        return null
    }

    // ---------------------------------------------------------
    // Build android.resource:// URI
    // ---------------------------------------------------------
    private fun rawUri(context: Context, resId: Int): Uri =
        "android.resource://${context.packageName}/$resId".toUri().also {
            Timber.tag(TAG).d("Generated raw URI: $it")
        }
}


 */