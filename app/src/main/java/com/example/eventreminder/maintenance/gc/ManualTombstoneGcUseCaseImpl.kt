package com.example.eventreminder.maintenance.gc

import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.sync.remote.TombstoneRemoteDataSource
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.days
import com.example.eventreminder.logging.DELETE_TAG
import com.example.eventreminder.maintenance.gc.ManualTombstoneGcUseCase


/**
 * Default implementation of manual tombstone garbage collection.
 *
 * User-triggered, destructive, fully logged.
 */
class ManualTombstoneGcUseCaseImpl @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val remoteDataSource: TombstoneRemoteDataSource
) : ManualTombstoneGcUseCase {

    private companion object {
        const val TAG = "ManualTombstoneGcUseCaseImpl"
        const val FN_RUN = "run"
    }

    override suspend fun run(
        nowEpochMillis: Long,
        retentionDays: Int
    ): TombstoneGcReport {

        val startedAt = System.currentTimeMillis()
        val cutoffEpochMillis =
            nowEpochMillis - retentionDays.days.inWholeMilliseconds

        Timber.tag(TAG).i("ManualTombstoneGcUseCaseImpl::%s - STARTED retentionDays=%d cutoff=%d", FN_RUN, retentionDays, cutoffEpochMillis)
        Timber.tag(DELETE_TAG).i("ManualTombstoneGcUseCaseImpl::%s - STARTED retentionDays=%d cutoff=%d", FN_RUN, retentionDays, cutoffEpochMillis)

        // ---------------------------------------------------------
        // Phase 1: Local scan
        // ---------------------------------------------------------
        val localTombstones = reminderRepository.getDeletedBefore(cutoffEpochMillis = cutoffEpochMillis)

        Timber.tag(TAG).d("ManualTombstoneGcUseCaseImpl::%s - Local scan candidates=%d", FN_RUN, localTombstones.size)
        Timber.tag(DELETE_TAG).d("ManualTombstoneGcUseCaseImpl::%s - Local scan candidates=%d", FN_RUN, localTombstones.size)

        // ---------------------------------------------------------
        // Phase 2: Remote scan
        // ---------------------------------------------------------
        val remoteIds = try {
            remoteDataSource.findDeletedBefore(cutoffEpochMillis = cutoffEpochMillis)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "ManualTombstoneGcUseCaseImpl::%s - Remote scan FAILED", FN_RUN)
            emptyList()
        }

        Timber.tag(DELETE_TAG).d("ManualTombstoneGcUseCaseImpl::%s - Remote scan candidates=%d", FN_RUN, remoteIds.size)
        Timber.tag(TAG).d("ManualTombstoneGcUseCaseImpl::%s - Remote scan candidates=%d", FN_RUN, remoteIds.size)

        // ---------------------------------------------------------
        // Phase 3: Remote delete (FIRST)
        // ---------------------------------------------------------
        val deletedRemoteCount = try {
            val count = remoteDataSource.deleteByIds(ids = remoteIds)

            Timber.tag(DELETE_TAG).i("ManualTombstoneGcUseCaseImpl::%s - Remote delete SUCCESS deleted=%d", FN_RUN, count)
            Timber.tag(TAG).i("ManualTombstoneGcUseCaseImpl::%s - Remote delete SUCCESS deleted=%d", FN_RUN, count)
            count
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "ManualTombstoneGcUseCaseImpl::%s - Remote delete FAILED", FN_RUN)
            0
        }

        // ---------------------------------------------------------
        // Phase 4: Local hard delete
        // ---------------------------------------------------------
        val localIds = localTombstones.map { it.id }

        if (localIds.isNotEmpty()) {
            Timber.tag(DELETE_TAG).w("ManualTombstoneGcUseCaseImpl::%s - LOCAL HARD DELETE count=%d", FN_RUN, localIds.size)
            Timber.tag(TAG).w("ManualTombstoneGcUseCaseImpl::%s - LOCAL HARD DELETE count=%d", FN_RUN, localIds.size)

            reminderRepository.hardDeleteByIds(ids = localIds)
        } else {
            Timber.tag(DELETE_TAG).d("ManualTombstoneGcUseCaseImpl::%s - No local hard deletes required", FN_RUN)
            Timber.tag(TAG).d("ManualTombstoneGcUseCaseImpl::%s - No local hard deletes required", FN_RUN)
        }

        // ---------------------------------------------------------
        // Phase 5: Report
        // ---------------------------------------------------------
        val finishedAt = System.currentTimeMillis()

        Timber.tag(DELETE_TAG).i("ManualTombstoneGcUseCaseImpl::%s - COMPLETED durationMs=%d localDeleted=%d remoteDeleted=%d", FN_RUN, finishedAt - startedAt, localIds.size, deletedRemoteCount)
        Timber.tag(TAG).i("ManualTombstoneGcUseCaseImpl::%s - COMPLETED durationMs=%d localDeleted=%d remoteDeleted=%d", FN_RUN, finishedAt - startedAt, localIds.size, deletedRemoteCount)

        return TombstoneGcReport(
            cutoffEpochMillis = cutoffEpochMillis,
            retentionDays = retentionDays,

            localCandidates = localTombstones.size,
            remoteCandidates = remoteIds.size,

            deletedLocal = localIds.size,
            deletedRemote = deletedRemoteCount,

            skipped = remoteIds.size - deletedRemoteCount,
            failedRemoteDeletes = remoteIds.size - deletedRemoteCount,

            startedAtEpochMillis = startedAt,
            finishedAtEpochMillis = finishedAt
        )
    }
}
