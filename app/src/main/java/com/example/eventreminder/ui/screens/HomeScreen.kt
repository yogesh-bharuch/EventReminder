package com.example.eventreminder.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.eventreminder.R
import com.example.eventreminder.navigation.*
import com.example.eventreminder.pdf.PdfViewModel
import com.example.eventreminder.ui.components.HomeBottomTray
import com.example.eventreminder.ui.components.events.EventsListGrouped
import com.example.eventreminder.ui.components.home.BirthdayEmptyState
import com.example.eventreminder.ui.components.home.HomeScaffold
import com.example.eventreminder.ui.viewmodels.GroupedEventsViewModel
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import com.example.eventreminder.logging.SAVE_TAG
import com.example.eventreminder.logging.SYNC_TAG


private const val TAG = "HomeScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    reminderVm: ReminderViewModel,
    pdfviewModel: PdfViewModel = hiltViewModel()
) {
    Timber.tag(TAG).d("HomeScreen composed")

    val context = LocalContext.current
    val activity = context as Activity
    val isSyncing by reminderVm.isSyncing.collectAsState()
    val isBackingUp by reminderVm.isBackingUp.collectAsState()
    val isRestoring by reminderVm.isRestoring.collectAsState()
    val isGeneratingPdf by pdfviewModel.isGeneratingPdf.collectAsState()


    // ---------------------------------------------------------
    // Snackbar Host (ViewModel → HomeScreen)
    // ---------------------------------------------------------
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(reminderVm) {
        //Timber.tag(SAVE_TAG).d("📥 HomeScreen UiEvent collector STARTED. [HomeScreen.kt::UiEventCollector]")

        reminderVm.events.collect { event ->
            when (event) {

                is ReminderViewModel.UiEvent.SaveSuccess -> {
                    Timber.tag(SAVE_TAG).d("🔔 Snackbar → ${event.message} [HomeScreen.kt::LaunchedEffect(reminderVm)]")
                    snackbarHostState.showSnackbar(event.message)
                    reminderVm.clearUiEvent()
                }

                is ReminderViewModel.UiEvent.SaveError -> {
                    Timber.tag(SAVE_TAG).d("❌ Snackbar → ${event.message} [HomeScreen.kt::LaunchedEffect(reminderVm)]")
                    snackbarHostState.showSnackbar(event.message)
                    reminderVm.clearUiEvent()
                }

                is ReminderViewModel.UiEvent.ShowMessage -> {
                    Timber.tag(SAVE_TAG).d("ℹ️ Snackbar → ${event.message} [HomeScreen.kt::LaunchedEffect(reminderVm)]")
                    snackbarHostState.showSnackbar(event.message)
                    reminderVm.clearUiEvent()
                }

                ReminderViewModel.UiEvent.Consumed -> {
                    // Intentionally ignored — used only to clear replay cache
                }

            }
        }
    }

    // ---------------------------------------------------------
    // Double Back Press to Exit App
    // ---------------------------------------------------------
    var backPressedOnce by remember { mutableStateOf(false) }

    BackHandler {
        if (backPressedOnce) {
            activity.finish()
        } else {
            backPressedOnce = true
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) {
            delay(1000)
            backPressedOnce = false
        }
    }

    /*// debug bottom tray action
    LaunchedEffect(Unit) {
        reminderVm.navigateToDebug.collect {
            navController.navigate(SchedulingDebugRoute)
        }
    }*/


    // ---------------------------------------------------------
    // Grouped Events
    // ---------------------------------------------------------
    val groupedVm: GroupedEventsViewModel = hiltViewModel()
    val groupedSections by groupedVm.groupedEvents.collectAsState()

    // ---------------------------------------------------------
    // PDF Open Handler
    // ---------------------------------------------------------
    LaunchedEffect(Unit) {
        pdfviewModel.openPdfEvent.collect { uri ->
            Timber.tag(TAG).d("Opening PDF → $uri")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Unable to open PDF")
            }
        }
    }

    // =============================================================
    // MAIN SCAFFOLD
    // =============================================================
    HomeScaffold(
        snackbarHostState = snackbarHostState,
        onNewEventClick = {
            navController.navigate(AddEditReminderRoute())
        },
        onSignOut = {
            Timber.tag(TAG).d("Signing out")
            FirebaseAuth.getInstance().signOut()
            navController.navigate(LoginRoute) {
                popUpTo(HomeRoute) { inclusive = true }
            }
        },
        onManageRemindersClick = {
            navController.navigate(ReminderManagerRoute)
        },
        bottomBar = {
            HomeBottomTray(
                isSyncing = isSyncing,
                isBackingUp = isBackingUp,
                isRestoring = isRestoring,
                isGeneratingPdf = isGeneratingPdf,
                onCleanupClick = {
                    coroutineScope.launch {
                        navController.navigate(SchedulingDebugRoute)
                        //reminderVm.cleanupOldReminders()
                        //snackbarHostState.showSnackbar("Old reminders cleaned")
                    }
                },
                onGeneratePdfClick = {
                    coroutineScope.launch {
                        pdfviewModel.allAlarmsReport()
                    }
                },
                onExportClick = {
                    coroutineScope.launch {
                        //reminderVm.exportRemindersCsv()
                        //pdfviewModel.runReminderListReport()
                        snackbarHostState.showSnackbar("Export complete")
                    }
                },
                onSyncClick = {
                    coroutineScope.launch {
                        Timber.tag(SYNC_TAG).i("▶️ Sync clicked [HomeScreen.kt::onSyncClick]")
                        reminderVm.syncRemindersWithServer()
                    }
                },
                onBackupClick = {
                    coroutineScope.launch {
                        reminderVm.backupReminders(context)
                    }
                },
                onRestoreClick = {
                    coroutineScope.launch {
                        reminderVm.restoreReminders(context)
                    }
                }
            )
        }
    ) { modifier ->

        // =============================================================
        // SCREEN BODY CONTENT
        // =============================================================
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {

            // HEADER — Show Firebase Email only
            val email = FirebaseAuth.getInstance().currentUser?.email ?: "Guest"

            Row(Modifier.padding(bottom = 8.dp)) {
                Text("Welcome: ", fontSize = 12.sp)
                Text(email, fontSize = 10.sp)
            }

            HorizontalDivider(thickness = 1.dp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            // Empty State or List
            if (groupedSections.isEmpty()) {

                BirthdayEmptyState()

            } else {

                // Button → Open Last Reminder
                Row {
                    Button(
                        onClick = {
                            val lastReminder = groupedSections
                                .flatMap { it.events }
                                .maxByOrNull { it.eventEpochMillis }

                            if (lastReminder != null) {
                                navController.navigate(
                                    PixelPreviewRouteString(reminderIdString = lastReminder.id)
                                )
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("No reminders found")
                                }
                            }
                        }
                    ) {
                        Text("Open last card")
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Events List
                EventsListGrouped(
                    sections = groupedSections,
                    viewModel = reminderVm,
                    onClick = { id ->
                        navController.navigate(
                            AddEditReminderRoute(eventId = id)
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// =============================================================
// Sound Test Buttons
// =============================================================
@Composable
fun SoundTestButtons(context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        fun play(resId: Int) {
            MediaPlayer.create(context, resId)?.apply {
                setOnCompletionListener { release() }
                start()
            }
        }

        Button(onClick = { play(R.raw.birthday) }) { Text("Play Birthday Sound") }
        Button(onClick = { play(R.raw.anniversary) }) { Text("Play Anniversary Sound") }
        Button(onClick = { play(R.raw.medicine) }) { Text("Play Medicine Sound") }
        Button(onClick = { play(R.raw.meeting) }) { Text("Play Meeting Sound") }
        Button(onClick = { play(R.raw.workout) }) { Text("Play Workout Sound") }
    }
}
