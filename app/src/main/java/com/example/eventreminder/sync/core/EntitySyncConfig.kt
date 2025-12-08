package com.example.eventreminder.sync.core


data class EntitySyncConfig<Local : Any>(
    val key: String,
    val direction: SyncDirection,
    val conflictStrategy: ConflictStrategy,
    val daoAdapter: SyncDaoAdapter<Local>,
    val getCollectionRef: () -> com.google.firebase.firestore.CollectionReference,
    val toRemote: (Local, userId: String) -> Map<String, Any?>,
    val fromRemote: (id: String, data: Map<String, Any?>) -> Local,
    val getLocalId: (Local) -> String,
    val getUpdatedAt: (Local) -> Long,
    val isDeleted: (Local) -> Boolean,
    val getLocalUpdatedAt: suspend (String) -> Long?
)

/*
* ✅ 1. EntitySyncConfig.kt
Defines the rules for syncing one entity type (e.g., reminders).
It tells SyncEngine:
How to convert Local → Firestore (toRemote)
How to convert Firestore → Local (fromRemote)
How to get IDs & timestamps
How to detect deleted items
Which DAO adapter to use
Which conflict strategy to apply
Where in Firestore the collection lives
👉 This is the blueprint for syncing one table.*/