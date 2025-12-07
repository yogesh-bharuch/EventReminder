package com.example.eventreminder.sync.config

// =============================================================
// Imports
// =============================================================
import com.example.eventreminder.data.local.ReminderDao
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.sync.core.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import timber.log.Timber

/**
 * ReminderSyncConfig
 *
 * Provides EntitySyncConfig<EventReminder> for SyncEngine.
 *
 * Firestore model:
 * Collection: Reminders/
 * DocumentID: <id>
 *
 * Required fields:
 * - uid
 * - id
 * - updatedAt (Timestamp: epoch millis)
 * - isDeleted
 */
object ReminderSyncConfig {

    private const val TAG = "ReminderSyncConfig"

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

            // ------------------------------------------------------------
            // Local → Remote
            // ------------------------------------------------------------
            toRemote = { local, userId ->

                // Convert millis -> Timestamp(seconds, nanoseconds)
                val updatedMillis = local.updatedAt
                val ts = Timestamp(
                    updatedMillis / 1000,
                    ((updatedMillis % 1000) * 1_000_000).toInt()
                )

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

                    // Sync-critical fields
                    "updatedAt" to ts,          // MUST be Timestamp
                    "isDeleted" to local.isDeleted
                )
            },

            // ------------------------------------------------------------
            // Remote → Local
            // ------------------------------------------------------------
            fromRemote = { id, data ->

                // Normalize remote updatedAt into epoch millis
                val updatedAt: Long = when (val raw = data["updatedAt"]) {
                    is Timestamp -> raw.toDate().time
                    is Number -> raw.toLong()
                    is String -> raw.toLongOrNull() ?: System.currentTimeMillis()
                    else -> System.currentTimeMillis()
                }

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
                        backgroundUri = data["backgroundUri"] as? String,
                        isDeleted = data["isDeleted"] as? Boolean ?: false,
                        updatedAt = updatedAt
                    )
                } catch (t: Throwable) {

                    Timber.tag(TAG).e(t, "fromRemote: Failed to parse remote doc id=%s", id)

                    EventReminder(
                        id = id.toLong(),
                        title = "",
                        description = null,
                        eventEpochMillis = System.currentTimeMillis(),
                        timeZone = "UTC",
                        repeatRule = null,
                        reminderOffsets = listOf(0L),
                        enabled = true,
                        backgroundUri = null,
                        isDeleted = data["isDeleted"] as? Boolean ?: false,
                        updatedAt = updatedAt
                    )
                }
            },

            // Unique ID extractor
            getLocalId = { event -> event.id.toString() },

            // Local timestamp extractor
            getUpdatedAt = { event -> event.updatedAt },

            // Deletion flag
            isDeleted = { event -> event.isDeleted },

            // ------------------------------------------------------------
            // Needed for conflict resolution inside SyncEngine
            // ------------------------------------------------------------
            getLocalUpdatedAt = { id ->
                daoAdapter.getLocalUpdatedAt(id)
            }
        )
    }
}
