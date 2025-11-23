package com.example.eventreminder.ui.screens

// =============================================================
// Imports
// =============================================================
import android.app.Activity
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
import android.util.Log
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.eventreminder.navigation.*
import com.example.eventreminder.pdf.PdfViewModel
import com.example.eventreminder.ui.components.BirthdayEmptyState
import com.example.eventreminder.ui.components.EventsListGrouped
import com.example.eventreminder.ui.components.HomeBottomTray
import com.example.eventreminder.ui.components.HomeScaffold
import com.example.eventreminder.ui.viewmodels.GroupedEventsViewModel
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import android.content.Intent
import android.net.Uri


// =============================================================
// HomeScreen
// =============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    reminderVm: ReminderViewModel = hiltViewModel(),
    pdfviewModel: PdfViewModel = hiltViewModel()
) {
    val TAG = "HomeScreen"
    Timber.tag(TAG).d("Rendering HomeScreen")

    val context = LocalContext.current
    var backPressedOnce by remember { mutableStateOf(false) }

    // ---------------------------------------------------------
    // 🔙 Double-back exit handling
    // ---------------------------------------------------------
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
    // Snackbar + coroutine
    // ---------------------------------------------------------
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // ---------------------------------------------------------
    // Grouped ViewModel state
    // ---------------------------------------------------------
    val groupedVm: GroupedEventsViewModel = hiltViewModel()
    val groupedSections by groupedVm.groupedEvents.collectAsState()

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
    // =========================================================
    // HomeScaffold (WITH correct bottomBar placement)
    // =========================================================
    HomeScaffold(
        onNewEventClick = { navController.navigate(AddEditReminderRoute()) },
        snackbarHostState = snackbarHostState,

        onSignOut = {
            FirebaseAuth.getInstance().signOut()
            navController.navigate(LoginRoute) {
                popUpTo(HomeRoute) { inclusive = true }
            }
        },

        onManageRemindersClick = { navController.navigate(ReminderManagerRoute) },

        // ---------------------------------------------------------
        // ⭐ BottomTray goes HERE — correct place
        // ---------------------------------------------------------
        bottomBar = {
            HomeBottomTray(
                onCleanupClick = {
                    coroutineScope.launch { reminderVm.cleanupOldReminders() }
                },
                onGeneratePdfClick = {
                    Log.d("PDF URI", "pdf reportclickrd")
                    coroutineScope.launch {
                        pdfviewModel.runTodo3RealReport() }
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

        // =====================================================
        // MAIN CONTENT
        // =====================================================
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {

            // ---------------------------------------------------------
            // Header
            // ---------------------------------------------------------
            Row(Modifier.padding(bottom = 8.dp)) {
                Text("Welcome: ", fontSize = 12.sp)
                Text(
                    FirebaseAuth.getInstance().currentUser?.email ?: "Guest",
                    fontSize = 10.sp
                )
            }

            HorizontalDivider(thickness = 1.dp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            // ---------------------------------------------------------
            // Empty State OR Event List
            // ---------------------------------------------------------
            if (groupedSections.isEmpty()) {

                BirthdayEmptyState()

            } else {

                Button(
                    onClick = {
                        //navController.navigate(DebugRoute)
                        navController.navigate(CardRoute(reminderId = 26))
                    }
                ) {
                    Text("Developer Tools")
                }

                Spacer(Modifier.height(10.dp))

                EventsListGrouped(
                    sections = groupedSections,
                    onClick = { id ->
                        navController.navigate(AddEditReminderRoute(id.toString()))
                    },
                    onDelete = { id ->
                        coroutineScope.launch {
                            reminderVm.deleteEvent(id)
                            snackbarHostState.showSnackbar("Event deleted")
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
