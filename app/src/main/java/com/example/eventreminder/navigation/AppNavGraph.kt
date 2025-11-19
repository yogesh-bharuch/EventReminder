package com.example.eventreminder.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.eventreminder.ui.debug.DebugScreen
import com.example.firebaseloginmodule.FirebaseLoginEntry
import com.example.eventreminder.ui.screens.HomeScreen
import com.example.eventreminder.ui.screens.AddEditReminderScreen
import com.example.eventreminder.ui.screens.EventEntryScreen
import com.example.eventreminder.ui.screens.ReminderManagerScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: Any
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        // 🔐 Login
        composable<LoginRoute> {
            FirebaseLoginEntry(
                onLoginSuccess = {
                    navController.navigate(HomeRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                }
            )
        }

        // 🏠 Home
        composable<HomeRoute> {
            HomeScreen(navController)
        }

        // ⏰ Reminder manager
        composable<ReminderManagerRoute> {
            ReminderManagerScreen(onBack = { navController.popBackStack() })
        }

        // 🧪 Debug / Developer Tools
        composable<DebugScreen> {
            DebugScreen()
        }


        // 🎉 Event entry (add/edit)
        composable<AddEditReminderRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<AddEditReminderRoute>()

            AddEditReminderScreen(
                navController = navController,
                eventId = args.eventId
            )
        }
    }
}
