package com.example.eventreminder.data.delivery

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PdfDeliveryLedger"

/**
 * PdfDeliveryLedgerImpl
 *
 * Responsibility:
 * - DataStore-backed implementation of PdfDeliveryLedger is a file to store keys.
 *
 * Guarantees:
 * - Crash-safe persistence
 * - Atomic updates
 * - Single-writer model
 * - Deterministic state
 */
@Singleton
class PdfDeliveryLedgerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PdfDeliveryLedger {

    // ------------------------------------------------------------
    // Keys. - Key name: "next7_last_delivered_date"
    // stores the date as a string
    // ------------------------------------------------------------
    private object Keys {
        val NEXT7_LAST_DELIVERED = stringPreferencesKey("next7_last_delivered_date")
        /* //can be extend to store other values
        // val USER_NAME = stringPreferencesKey("user_name")
        // val LAUNCH_COUNT = intPreferencesKey("launch_count")
        */
    }

    // ------------------------------------------------------------
    // Streams
    /**
     * Flow stream of the last delivery date for the "Next 7 Days PDF".
     *
     * - Reads from DataStore asynchronously.
     * - Emits `LocalDate?` values:
     *   - `null` if no delivery date is stored.
     *   - Parsed `LocalDate` if a string value exists.
     * - Reactive: any change in DataStore automatically updates collectors.
     */
    // ------------------------------------------------------------
    override val next7DaysLastDelivered: Flow<LocalDate?> = context.pdfDeliveryDataStore.data.map { prefs ->
            prefs[Keys.NEXT7_LAST_DELIVERED]?.let { LocalDate.parse(it) }
        }
    /*// can be extend
    val userName: Flow<String?> = context.pdfDeliveryDataStore.data.map { prefs ->
        prefs[Keys.USER_NAME]
    }

    val launchCount: Flow<Int> = context.pdfDeliveryDataStore.data.map { prefs ->
        prefs[Keys.LAUNCH_COUNT] ?: 0
    }*/



    // ------------------------------------------------------------
    // Writes
    /**
     * Marks the "Next 7 Days PDF" as delivered for the given date.
     *
     * @param date The LocalDate representing when the PDF was delivered.
     *
     * Behavior:
     * - Stores the date as a string in DataStore under key `next7_last_delivered_date`.
     * - Operation is atomic and crash‑safe.
     * - Logs the event with Timber for traceability.
     */
    // ------------------------------------------------------------
    override suspend fun markNext7DaysDelivered(date: LocalDate) {
        context.pdfDeliveryDataStore.edit { prefs ->
            prefs[Keys.NEXT7_LAST_DELIVERED] = date.toString()
        }

        Timber.tag(TAG).i("NEXT7_DELIVERY_MARKED date=$date [PdfDeliveryLedgerImpl.kt::markNext7DaysDelivered]")
    }
    /* can be extend
    * suspend fun incrementLaunchCount() {
    context.pdfDeliveryDataStore.edit { prefs ->
        val current = prefs[Keys.LAUNCH_COUNT] ?: 0
        prefs[Keys.LAUNCH_COUNT] = current + 1
    }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.pdfDeliveryDataStore.edit { prefs ->
            prefs[Keys.DARK_MODE_ENABLED] = enabled
        }
    }
    */

    /**
     * Clears the stored delivery date for the "Next 7 Days PDF".
     *
     * Behavior:
     * - Removes the key `next7_last_delivered_date` from DataStore.
     * - Effectively resets the ledger (no delivery recorded).
     * - Operation is atomic and crash‑safe.
     * - Logs the event with Timber for traceability.
     */
    override suspend fun clearNext7DaysDelivery() {
        context.pdfDeliveryDataStore.edit { prefs ->
            prefs.remove(Keys.NEXT7_LAST_DELIVERED)
        }

        Timber.tag(TAG).i("NEXT7_DELIVERY_CLEARED [PdfDeliveryLedgerImpl.kt::clearNext7DaysDelivery]")
    }
    /* can be extend
    suspend fun clearUserName() {
    context.pdfDeliveryDataStore.edit { prefs ->
        prefs.remove(Keys.USER_NAME)
    }
    }

    suspend fun resetLaunchCount() {
        context.pdfDeliveryDataStore.edit { prefs ->
            prefs.remove(Keys.LAUNCH_COUNT)
        }
    }
    * */
}
