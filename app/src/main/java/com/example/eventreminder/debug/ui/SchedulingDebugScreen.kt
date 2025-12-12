package com.example.eventreminder.debug.ui

// =============================================================
// Scheduling Debug UI — Compose Screen + ViewModel
// Developer-only debug overlay to exercise the SchedulingTestHarness
// and SchedulingDebugHelper and to view reminder state & basic logs.
// =============================================================

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.debug.SchedulingTestHarness
import com.example.eventreminder.scheduler.ReminderSchedulingEngine
import com.example.eventreminder.util.NextOccurrenceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val TAG = "SchedulingDebugUI"

// =============================================================
// ViewModel
// =============================================================
@HiltViewModel
class SchedulingDebugViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val harness: SchedulingTestHarness,
    private val engine: ReminderSchedulingEngine
) : ViewModel() {

    private val _reminders = MutableStateFlow<List<EventReminder>>(emptyList())
    val reminders: StateFlow<List<EventReminder>> = _reminders.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z")

    init {
        refreshReminders()
        log("SchedulingDebugViewModel initialized")
    }

    // -----------------------------------------
    // Log helpers
    // -----------------------------------------
    private fun log(msg: String) {
        addToLog(msg)
    }

    fun logUserAction(msg: String) {
        addToLog("USER → $msg")
    }

    private fun addToLog(msg: String) {
        val entry = "${Instant.now()}: $msg"
        Timber.tag(TAG).d(entry)
        _logs.value = (_logs.value + entry).takeLast(200)
    }

    // -----------------------------------------
    // Load reminders
    // -----------------------------------------
    fun refreshReminders() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val list = repo.getAllOnce()
            _reminders.value = list
            log("Loaded ${list.size} reminders from DB")
        } catch (t: Throwable) {
            log("Failed to load reminders: ${t.message}")
        }
    }

    // -----------------------------------------
    // Test Actions
    // -----------------------------------------
    fun simulateSave(reminder: EventReminder) = viewModelScope.launch(Dispatchers.IO) {
        try {
            log("Simulating SAVE for ${reminder.id}")
            harness.simulateSave(reminder)
            log("simulateSave completed for ${reminder.id}")
            refreshReminders()
        } catch (t: Throwable) {
            log("simulateSave failed: ${t.message}")
        }
    }

    fun simulateBoot(nowEpochMillis: Long) = viewModelScope.launch(Dispatchers.IO) {
        try {
            log("Simulating BOOT at $nowEpochMillis")
            harness.simulateBoot(nowEpochMillis)
            log("simulateBoot completed")
            refreshReminders()
        } catch (t: Throwable) {
            log("simulateBoot failed: ${t.message}")
        }
    }

    fun simulateRepeat(reminderId: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            log("Simulating REPEAT for $reminderId")
            harness.simulateRepeatTrigger(reminderId)
            log("simulateRepeat completed for $reminderId")
            refreshReminders()
        } catch (t: Throwable) {
            log("simulateRepeat failed: ${t.message}")
        }
    }

    fun showNextOccurrence(reminder: EventReminder) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val next = NextOccurrenceCalculator.nextOccurrence(
                reminder.eventEpochMillis,
                reminder.timeZone,
                reminder.repeatRule
            )
            if (next == null) {
                log("Next occurrence for ${reminder.id}: NONE")
            } else {
                val human = Instant.ofEpochMilli(next).atZone(ZoneId.of(reminder.timeZone))
                    .format(formatter)
                log("Next for ${reminder.id}: $human")
            }
        } catch (t: Throwable) {
            log("Next occurrence failed: ${t.message}")
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}

// =============================================================
// Screen
// =============================================================
@Composable
fun SchedulingDebugScreen(
    viewModel: SchedulingDebugViewModel
) {
    val reminders by viewModel.reminders.collectAsState()
    val logs by viewModel.logs.collectAsState()

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.padding(12.dp)) {

            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Scheduling Debug", style = MaterialTheme.typography.titleLarge)

                Row {
                    Button(onClick = { viewModel.refreshReminders() }, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Refresh")
                    }
                    Button(onClick = { viewModel.clearLogs() }) { Text("Clear Logs") }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Reminders
            Card(Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.padding(8.dp)) {

                    Text("Reminders (${reminders.size})", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    LazyColumn {
                        items(reminders, key = { it.id }) { rem ->

                            ReminderRow(
                                rem = rem,
                                vm = viewModel,
                                onSimSave = { viewModel.simulateSave(it) },
                                onSimRepeat = { viewModel.simulateRepeat(rem.id) },
                                onShowNext = { viewModel.showNextOccurrence(rem) }
                            )

                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Logs
            Card(Modifier.fillMaxWidth().height(200.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("Logs", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))

                    LazyColumn {
                        items(logs) { line ->
                            Text(line, Modifier.padding(2.dp))
                        }
                    }
                }
            }
        }
    }
}

// =============================================================
// ReminderRow (LazyRow buttons)
// =============================================================
@Composable
private fun ReminderRow(
    rem: EventReminder,
    vm: SchedulingDebugViewModel,
    onSimSave: (EventReminder) -> Unit,
    onSimRepeat: () -> Unit,
    onShowNext: () -> Unit
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {

        Column(Modifier.padding(12.dp)) {

            Text(rem.title, fontWeight = FontWeight.Bold)
            Text("ID: ${rem.id}", style = MaterialTheme.typography.bodySmall)
            Text("When: ${Instant.ofEpochMilli(rem.eventEpochMillis)}", style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                item {
                    Button(onClick = {
                        vm.logUserAction("Sim Save clicked for ${rem.id}")
                        onSimSave(rem)
                    }) { Text("Sim Save") }
                }

                item {
                    Button(onClick = {
                        vm.logUserAction("Sim Repeat clicked for ${rem.id}")
                        onSimRepeat()
                    }) { Text("Sim Repeat") }
                }

                item {
                    Button(onClick = {
                        vm.logUserAction("Show Next clicked for ${rem.id}")
                        onShowNext()
                    }) { Text("Next") }
                }
            }
        }
    }
}

// Utility
private fun SchedulingDebugViewModel.simulateTimeNowForUi(): Long = System.currentTimeMillis()
