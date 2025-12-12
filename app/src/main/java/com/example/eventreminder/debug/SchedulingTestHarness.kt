package com.example.eventreminder.debug

// =============================================================
// SchedulingTestHarness
// -------------------------------------------------------------
// A developer-only utility for simulating internal scheduling
// behaviors WITHOUT needing real system alarms to fire.
//
// Features:
//  • simulateSave(reminder)        — behaves like ViewModel saving
//  • simulateBoot(nowMillis)       — behaves like BootReceiver
//  • simulateRepeatTrigger(id)     — behaves like ReminderReceiver
//  • simulateTimeAdvance(minutes)  — optional time travel helper
//
// Used for debugging SchedulingEngine end-to-end:
//  • Validates offset scheduling
//  • Validates missed detection
//  • Validates repeat rescheduling
//  • Validates fire-state persistence
//
// NOTE: Does NOT schedule real alarms. It calls into the engine,
// which would normally schedule alarms, but the purpose is to
// inspect logs & behaviors without actual trigger delays.
//
// Matches project standards: imports, headers, inline comments,
// Hilt DI, and UUID-only reminder IDs.
// =============================================================

import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.scheduler.ReminderSchedulingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SchedulingTestHarness"

@Singleton
class SchedulingTestHarness @Inject constructor(
    private val repo: ReminderRepository,
    private val engine: ReminderSchedulingEngine
) {

    // =============================================================
    // SIMULATE "SAVE" FLOW
    // -------------------------------------------------------------
    // This mimics what ViewModel does after insert/update.
    // =============================================================
    suspend fun simulateSave(reminder: EventReminder) = withContext(Dispatchers.IO) {
        Timber.tag(TAG).i("=== TEST: Simulating SAVE for ${reminder.id} ===")

        // Engine handles:
        //  • Cancel old alarms
        //  • Compute next occurrence
        //  • Schedule new future offsets
        engine.processSavedReminder(reminder = reminder)

        Timber.tag(TAG).i("=== SAVE simulation complete ===")
    }

    // =============================================================
    // SIMULATE BOOT RESTORE
    // -------------------------------------------------------------
    // Pass a custom timestamp to simulate device reboot at ANY time.
    // =============================================================
    suspend fun simulateBoot(nowEpochMillis: Long = System.currentTimeMillis()) =
        withContext(Dispatchers.IO) {

            Timber.tag(TAG).i("=== TEST: Simulating BOOT at $nowEpochMillis ===")

            val reminders = repo.getNonDeletedEnabled()

            reminders.forEach { reminder ->
                Timber.tag(TAG).i("--- Boot processing reminder=${reminder.id} ---")

                engine.processBootRestore(
                    reminder = reminder,
                    nowEpochMillis = nowEpochMillis
                )
            }

            Timber.tag(TAG).i("=== BOOT simulation complete ===")
        }

    // =============================================================
    // SIMULATE REPEAT TRIGGER
    // -------------------------------------------------------------
    // This mimics ReminderReceiver firing for repeating reminders.
    // =============================================================
    suspend fun simulateRepeatTrigger(reminderId: String) =
        withContext(Dispatchers.IO) {

            Timber.tag(TAG).i("=== TEST: Simulating REPEAT trigger for $reminderId ===")
            engine.processRepeatTrigger(reminderId = reminderId)
            Timber.tag(TAG).i("=== REPEAT simulation complete ===")
        }

    // =============================================================
    // SIMULATE TIME ADVANCE (Developer Tool)
    // -------------------------------------------------------------
    // Allows testing “future conditions” easily by moving ahead
    // in time relative to event time calculations.
    // =============================================================
    fun simulateTimeAdvance(minutes: Long): Long {
        val now = System.currentTimeMillis()
        val future = now + (minutes * 60_000)

        Timber.tag(TAG).i(
            "=== TEST: Time advance → now=${format(now)} future=${format(future)} ==="
        )

        return future
    }

    // =============================================================
    // FORMAT HELPER
    // =============================================================
    private fun format(epoch: Long): String {
        return Instant.ofEpochMilli(epoch)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .toString()
    }
}
