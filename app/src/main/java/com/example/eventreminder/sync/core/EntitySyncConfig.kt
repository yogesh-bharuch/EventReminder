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
