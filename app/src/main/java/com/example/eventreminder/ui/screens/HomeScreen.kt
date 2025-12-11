package com.example.eventreminder.ui.screens

// =============================================================
// Imports
// =============================================================
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

// =============================================================
// Constants
// =============================================================
private const val TAG = "HomeScreen"

// =============================================================
// HomeScreen Composable
// =============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    reminderVm: ReminderViewModel,
    pdfviewModel: PdfViewModel = hiltViewModel()
) {
    Timber.tag(TAG).d("Rendering HomeScreen")
    Timber.tag("TRACE").e("🏠 HomeScreen composed/recomposed")

    val context = LocalContext.current
    val activity = context as Activity

    // ---------------------------------------------------------
    // Snackbar Host (ViewModel → HomeScreen)
    // ---------------------------------------------------------
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        Timber.tag(TAG).d("Home snackbar collector active")

        reminderVm.snackbarEvent.collectLatest { message ->
            Timber.tag(TAG).d("Snackbar message received → $message")
            snackbarHostState.showSnackbar(message = message)
            reminderVm.clearSnackbar()
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

        // FAB
        onNewEventClick = {
            navController.navigate(AddEditReminderRoute())
        },

        // Sign Out (FirebaseAuth only)
        onSignOut = {
            Timber.tag(TAG).i("Logout clicked — signing out Firebase user")
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
                onCleanupClick = {
                    coroutineScope.launch {
                        reminderVm.cleanupOldReminders()
                        snackbarHostState.showSnackbar("Old reminders cleaned")
                    }
                },
                onGeneratePdfClick = {
                    coroutineScope.launch {
                        pdfviewModel.runTodo3RealReport()
                        snackbarHostState.showSnackbar("PDF generated")
                    }
                },
                onExportClick = {
                    coroutineScope.launch {
                        reminderVm.exportRemindersCsv()
                        snackbarHostState.showSnackbar("Export complete")
                    }
                },
                onSyncClick = {
                    coroutineScope.launch {
                        reminderVm.syncRemindersWithServer()
                        snackbarHostState.showSnackbar("Sync requested")
                    }
                },
                onBackupClick = {
                    coroutineScope.launch {
                        reminderVm.backupReminders(context)
                        snackbarHostState.showSnackbar("Backup completed")
                    }
                },
                onRestoreClick = {
                    coroutineScope.launch {
                        reminderVm.restoreReminders(context)
                        snackbarHostState.showSnackbar("Restore completed")
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

            // HEADER — Show FirebaseEmail only
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
