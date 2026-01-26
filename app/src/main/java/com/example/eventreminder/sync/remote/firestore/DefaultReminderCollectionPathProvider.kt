// =============================================================
// DefaultReminderCollectionPathProvider.kt
// =============================================================

package com.example.eventreminder.sync.remote.firestore

// =============================================================
// Imports
// =============================================================
import com.example.eventreminder.sync.remote.ReminderCollectionPathProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides Firestore collection reference for reminders.
 *
 * IMPORTANT:
 * Must match SyncEngine collection path exactly.
 */
@Singleton
class DefaultReminderCollectionPathProvider @Inject constructor() :
    ReminderCollectionPathProvider {

    override fun remindersCollection(
        firestore: FirebaseFirestore,
        userId: String
    ): CollectionReference {
        // 🔑 Single source of truth — same as SyncEngine
        return firestore.collection("Reminders")
    }
}
