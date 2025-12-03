package com.example.eventreminder.ui.screens

import android.app.Activity
import android.content.Intent
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.eventreminder.navigation.*
import com.example.eventreminder.pdf.PdfViewModel
import com.example.eventreminder.ui.components.*
import com.example.eventreminder.ui.viewmodels.GroupedEventsViewModel
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * HomeScreen
 * ----------
 * - Displays grouped reminders
 * - Hosts the FAB to create new reminders
 * - Collects snackbar events emitted after Add/Edit Reminder actions
 * - Uses the SAME shared ReminderViewModel (scoped to HomeGraphRoute)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    reminderVm: ReminderViewModel,      // ⭐ Shared VM injected from NavGraph
    pdfviewModel: PdfViewModel = hiltViewModel()
) {
    val TAG = "HomeScreen"
    Timber.tag(TAG).d("Rendering HomeScreen with VM: $reminderVm")

    val context = LocalContext.current

    // ---------------------------------------------------------
    // 🔔 SNACKBAR HOST
    // Collects snackbar events when Add/EditReminderScreen sends them
    // ---------------------------------------------------------
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(true) {
        Timber.tag("HOME_SNACK").d("Collector ACTIVE")

        reminderVm.snackbarEvent.collectLatest { message ->
            Timber.tag("HOME_SNACK").d("Received: $message")
            snackbarHostState.showSnackbar(message)
            reminderVm.clearSnackbar()  // ⭐ Avoid duplicate replay
        }
    }

    // ---------------------------------------------------------
    // 🔙 DOUBLE BACK TO EXIT
    // ---------------------------------------------------------
    var backPressedOnce by remember { mutableStateOf(false) }

    BackHandler {
        if (backPressedOnce) {
            (context as? Activity)?.finish()
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
    // 🗂 GROUPED EVENTS VIEWMODEL (local VM)
    // ---------------------------------------------------------
    val groupedVm: GroupedEventsViewModel = hiltViewModel()
    val groupedSections by groupedVm.groupedEvents.collectAsState()

    // ---------------------------------------------------------
    // 📄 PDF OPEN EVENT
    // ---------------------------------------------------------
    LaunchedEffect(Unit) {
        pdfviewModel.openPdfEvent.collect { uri ->
            Timber.d("PDF URI → $uri")
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Timber.tag("HomeScreen").e(e, "Unable to open PDF")
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // ---------------------------------------------------------
    // 🏠 MAIN SCAFFOLD
    // ---------------------------------------------------------
    HomeScaffold(
        snackbarHostState = snackbarHostState,
        onNewEventClick = {
            // Navigate with no ID → Add Mode
            navController.navigate(AddEditReminderRoute())
        },
        onSignOut = {
            FirebaseAuth.getInstance().signOut()
            navController.navigate(LoginRoute) {
                popUpTo(HomeRoute) { inclusive = true }
            }
        },
        onManageRemindersClick = { navController.navigate(ReminderManagerRoute) },

        // Bottom tray with utility actions
        bottomBar = {
            HomeBottomTray(
                onCleanupClick = {
                    coroutineScope.launch { reminderVm.cleanupOldReminders() }
                },
                onGeneratePdfClick = {
                    coroutineScope.launch { pdfviewModel.runTodo3RealReport() }
                },
                onExportClick = {
                    coroutineScope.launch { reminderVm.exportRemindersCsv() }
                },
                onSyncClick = {
                    coroutineScope.launch { reminderVm.syncRemindersWithServer() }
                }
            )
        }
    ) { modifier ->

        // ---------------------------------------------------------
        // 📄 MAIN BODY CONTENT
        // ---------------------------------------------------------
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {

            // ---------------------------------------------------------
            // HEADER
            // ---------------------------------------------------------
            Row(Modifier.padding(bottom = 8.dp)) {
                Text("Welcome: ", fontSize = 12.sp)
                Text(FirebaseAuth.getInstance().currentUser?.email ?: "Guest", fontSize = 10.sp)
            }

            HorizontalDivider(thickness = 1.dp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            // ---------------------------------------------------------
            // EMPTY STATE OR REMINDER LIST
            // ---------------------------------------------------------
            if (groupedSections.isEmpty()) {

                BirthdayEmptyState()

            } else {

                // Button row (Pixel Preview navigation)
                Row {
                    Button(
                        onClick = {
                            navController.navigate(PixelPreviewRoute(reminderId = 54))
                        }
                    ) {
                        Text("New screen")
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Grouped Event List
                EventsListGrouped(
                    sections = groupedSections,
                    viewModel = reminderVm,
                    onClick = { id ->
                        navController.navigate(AddEditReminderRoute(id.toString()))
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
