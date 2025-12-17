// =============================================================
// ReminderCollectionPathProvider.kt
// =============================================================

package com.example.eventreminder.sync.remote

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Provides Firestore collection paths for reminder documents.
 *
 * Centralizes path logic so SyncEngine and GC always stay aligned.
 */
interface ReminderCollectionPathProvider {

    fun remindersCollection(
        firestore: FirebaseFirestore,
        userId: String
    ): CollectionReference
}
