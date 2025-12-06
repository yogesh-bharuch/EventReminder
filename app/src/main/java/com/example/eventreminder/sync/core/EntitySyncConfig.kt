package com.example.eventreminder.sync.core

/**
 * Configuration describing how to sync one specific Room entity type.
 */
data class EntitySyncConfig<Local>(

    /** Unique key identifying this entity table ("reminders"). */
    val key: String,

    /** Sync direction. */
    val direction: SyncDirection,

    /** Conflict resolution rule. */
    val conflictStrategy: ConflictStrategy,

    /** Adapter for Room operations. */
    val daoAdapter: SyncDaoAdapter<Local>,

    /**
     * Firestore collection reference builder.
     * For hybrid model → always "Reminders" flat collection.
     */
    val getCollectionRef: () -> com.google.firebase.firestore.CollectionReference,

    /** Mapping Room → Firestore document. */
    val toRemote: (Local, userId: String) -> Map<String, Any?>,

    /** Mapping Firestore document → Room entity. */
    val fromRemote: (id: String, data: Map<String, Any?>) -> Local,

    /** Extract entity ID (must match Firestore doc ID). */
    val getLocalId: (Local) -> String,

    /** Extract "updatedAt" timestamp for incremental sync. */
    val getUpdatedAt: (Local) -> Long,

    /** Identify if a local row is a tombstone deletion. */
    val isDeleted: (Local) -> Boolean
)
