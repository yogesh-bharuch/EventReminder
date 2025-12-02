package com.example.eventreminder.cards.pixelcanvas

/**
 * SafStorageHelper.kt
 *
 * Utilities to persist and read a SAF tree URI (persistable) using the project's DataStore.
 * - Saves URI as a string in Preferences DataStore
 * - Exposes a Flow<Uri?> to read the saved tree
 *
 * Uses Preference keys and the application's DataStore. This file is self-contained and safe.
 */

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "SafStorageHelper"

// DataStore name — integrates with your app. Adjust name if you already use DataStore namespaces.
private const val DATASTORE_NAME = "event_reminder_saf"

// Preferences key for storing the tree URI string
private val KEY_SAF_TREE_URI = stringPreferencesKey("saf_tree_uri")

// Extension property to access DataStore from Context. Reuse this across the app.
private val Context.safDataStore by preferencesDataStore(name = DATASTORE_NAME)

object SafStorageHelper {

    /**
     * Persist the provided treeUriString into DataStore so future saves are automatic.
     * Call this after you have taken persistableUriPermission for the uri.
     */
    suspend fun saveTreeUri(context: Context, treeUriString: String) {
        context.safDataStore.edit { prefs ->
            prefs[KEY_SAF_TREE_URI] = treeUriString
        }
    }

    /**
     * Clear saved tree URI from storage (if you want to reset behavior).
     */
    suspend fun clearTreeUri(context: Context) {
        context.safDataStore.edit { prefs ->
            prefs.remove(KEY_SAF_TREE_URI)
        }
    }

    /**
     * Exposes a Flow that emits the parsed Uri or null when no saved tree exists.
     */
    fun getTreeUriFlow(context: Context): Flow<Uri?> {
        return context.safDataStore.data.map { prefs ->
            prefs[KEY_SAF_TREE_URI]?.let { Uri.parse(it) }
        }
    }
}
