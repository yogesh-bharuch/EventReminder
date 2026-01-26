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
 * UUID–ONLY sync configuration.
 *
 * NOTE ON UID:
 * - SyncEngine provides userId separately
 * - fromRemote() creates EventReminder with placeholder uid
 * - DAO / Repository layer enforces the real UID
 */
object ReminderSyncConfig {

    private const val TAG = "ReminderSyncConfig"

    fun create(
        firestore: FirebaseFirestore,
        reminderDao: ReminderDao,
        userIdProvider: UserIdProvider        // ✅ REQUIRED
    ): EntitySyncConfig<EventReminder> {

        val daoAdapter = ReminderSyncDaoAdapter(
            dao = reminderDao,
            userIdProvider = userIdProvider    // ✅ FIX
        )

        return EntitySyncConfig(
            key = "reminders",
            direction = SyncDirection.BIDIRECTIONAL,
            conflictStrategy = ConflictStrategy.LATEST_UPDATED_WINS,
            daoAdapter = daoAdapter,

            // Firestore collection
            getCollectionRef = {
                firestore.collection("Reminders")
            },

            // -----------------------------------------------------------------
            // LOCAL → REMOTE (UUID)
            // -----------------------------------------------------------------
            toRemote = { local, userId ->

                val updatedMillis = local.updatedAt

                mapOf(
                    "uid" to userId,
                    "id" to local.id,
                    "title" to local.title,
                    "description" to local.description,
                    "eventEpochMillis" to local.eventEpochMillis,
                    "repeatRule" to local.repeatRule,
                    "reminderOffsets" to local.reminderOffsets,
                    "enabled" to local.enabled,
                    "timeZone" to local.timeZone,
                    "backgroundUri" to local.backgroundUri,
                    "updatedAt" to updatedMillis,
                    "isDeleted" to local.isDeleted
                )
            },

            // -----------------------------------------------------------------
            // REMOTE → LOCAL (UUID)
            // -----------------------------------------------------------------
            fromRemote = { id, data ->

                val updatedAt: Long = when (val raw = data["updatedAt"]) {
                    is Timestamp -> raw.toDate().time
                    is Number -> raw.toLong()
                    is String -> raw.toLongOrNull() ?: System.currentTimeMillis()
                    else -> System.currentTimeMillis()
                }

                try {
                    EventReminder(
                        uid = "", // placeholder — real UID enforced later
                        id = id,
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

                    Timber.tag(TAG).e(t, "fromRemote: Failed to parse remote doc id=$id")

                    EventReminder(
                        uid = "",
                        id = id,
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

            // -----------------------------------------------------------------
            // Helpers
            // -----------------------------------------------------------------
            getLocalId = { event -> event.id },
            getUpdatedAt = { event -> event.updatedAt },
            enabled = { event -> event.enabled },
            isDeleted = { event -> event.isDeleted },
            getLocalUpdatedAt = { idString ->
                daoAdapter.getLocalUpdatedAt(idString)
            }
        )
    }
}
