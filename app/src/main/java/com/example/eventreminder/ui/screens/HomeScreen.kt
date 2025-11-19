package com.example.eventreminder.ui.screens

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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.eventreminder.navigation.*
import com.example.eventreminder.ui.components.*
import com.example.eventreminder.ui.viewmodels.GroupedEventsViewModel
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    reminderVm: ReminderViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var backPressedOnce by remember { mutableStateOf(false) }

    // 🔙 Double-tap exit
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

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 🔄 ViewModel → grouped list
    val groupedVm: GroupedEventsViewModel = hiltViewModel()
    val groupedSections by groupedVm.groupedEvents.collectAsState()

    HomeScaffold(
        onNewEventClick = { navController.navigate(AddEditReminderRoute()) },
        snackbarHostState = snackbarHostState,
        onSignOut = {
            FirebaseAuth.getInstance().signOut()
            navController.navigate(LoginRoute) {
                popUpTo(HomeRoute) { inclusive = true }
            }
        },
        onManageRemindersClick = { navController.navigate(ReminderManagerRoute) }
    ) { modifier ->

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {

            // Header
            Row(Modifier.padding(bottom = 8.dp)) {
                Text("Welcome: ", fontSize = 12.sp)
                Text(FirebaseAuth.getInstance().currentUser?.email ?: "Guest", fontSize = 10.sp)
            }

            HorizontalDivider(thickness = 1.dp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            // Empty state
            if (groupedSections.isEmpty()) {
                BirthdayEmptyState()
                return@Column
            }

            Button(onClick = {
                //navController.navigate(DebugScreen)
                navController.navigate(CardDebugScreen)
            }) {
                Text("Developer Tools")
            }


            // ⭐ REPLACED ALL OLD LAZY COLUMN CODE WITH THIS:
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
                }
            )
        }
    }
}
