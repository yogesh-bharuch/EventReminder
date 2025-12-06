package com.example.eventreminder.sync.config

// =============================================================
// Imports
// =============================================================
import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.sync.core.*
import com.google.firebase.firestore.FirebaseFirestore
import timber.log.Timber

/**
 * ReminderSyncConfig
 *
 * Creates EntitySyncConfig<EventReminder> for use inside SyncConfig.
 *
 * Hybrid Firestore model used:
 * Collection: Reminders/
 * Document:  <id>
 *
 * Document fields MUST include:
 * - uid (owner)
 * - id (string)
 * - updatedAt (Long)
 * - isDeleted (Boolean)
 *
 * We currently map updatedAt = eventEpochMillis
 * We currently treat "isDeleted" as always false (v1).
 */
object ReminderSyncConfigFactory {

    private const val TAG = "ReminderSyncConfig"

    /**
     * Create the sync configuration for EventReminder.
     */
    fun create(
        firestore: FirebaseFirestore,
        reminderDao: ReminderDao
    ): EntitySyncConfig<EventReminder> {

        val daoAdapter = ReminderSyncDaoAdapter(reminderDao)

        return EntitySyncConfig(
            key = "reminders",
            direction = SyncDirection.BIDIRECTIONAL,
            conflictStrategy = ConflictStrategy.LATEST_UPDATED_WINS,
            daoAdapter = daoAdapter,

            // Firestore collection reference
            getCollectionRef = {
                firestore.collection("Reminders")
            },

            // --------------------------
            // Mapping Room → Firestore
            // --------------------------
            toRemote = { local, userId ->
                mapOf(
                    "uid" to userId,
                    "id" to local.id.toString(),
                    "title" to local.title,
                    "description" to local.description,
                    "eventEpochMillis" to local.eventEpochMillis,
                    "repeatRule" to local.repeatRule,
                    "reminderOffsets" to local.reminderOffsets,
                    "enabled" to local.enabled,
                    "timeZone" to local.timeZone,
                    "backgroundUri" to local.backgroundUri,

                    // Standard sync fields:
                    "updatedAt" to local.eventEpochMillis,
                    "isDeleted" to false
                )
            },

            // --------------------------
            // Mapping Firestore → Room
            // --------------------------
            fromRemote = { id, data ->
                try {
                    EventReminder(
                        id = id.toLong(),
                        title = data["title"] as? String ?: "",
                        description = data["description"] as? String?,
                        eventEpochMillis = (data["eventEpochMillis"] as? Number)?.toLong()
                            ?: System.currentTimeMillis(),
                        timeZone = data["timeZone"] as? String ?: "UTC",
                        repeatRule = data["repeatRule"] as? String?,
                        reminderOffsets = (data["reminderOffsets"] as? List<*>)?.mapNotNull {
                            (it as? Number)?.toLong()
                        } ?: listOf(0L),
                        enabled = data["enabled"] as? Boolean ?: true,
                        backgroundUri = data["backgroundUri"] as? String
                    )
                } catch (t: Throwable) {
                    Timber.tag(TAG).e(t, "fromRemote: Failed to parse remote doc id=%s", id)
                    // Fallback item (never null)
                    EventReminder(
                        id = id.toLong(),
                        title = "",
                        eventEpochMillis = System.currentTimeMillis(),
                        timeZone = "UTC",
                        reminderOffsets = listOf(0L)
                    )
                }
            },

            // Extract local ID
            getLocalId = { event -> event.id.toString() },

            // Extract updatedAt
            getUpdatedAt = { event -> event.eventEpochMillis },

            // isDeleted (v1 always false)
            isDeleted = { false }
        )
    }
}
