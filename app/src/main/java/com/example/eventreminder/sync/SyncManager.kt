package com.example.eventreminder.sync

import com.example.eventreminder.sync.core.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SyncManager
 *
 * Convenience wrapper around SyncEngine for manual/triggered syncs.
 */
@Singleton
class SyncManager @Inject constructor(
    private val syncEngine: SyncEngine
) {

    fun syncNow() {
        CoroutineScope(Dispatchers.IO).launch {
            syncEngine.syncAll()
        }
    }
}
