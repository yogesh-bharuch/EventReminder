package com.example.eventreminder.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation   // Typed Navigation API
import androidx.navigation.toRoute
import com.example.eventreminder.cards.pixelcanvas.ui.CardEditorScreen
import com.example.eventreminder.ui.screens.AddEditReminderScreen
import com.example.eventreminder.ui.screens.HomeScreen
import com.example.eventreminder.ui.screens.ReminderManagerScreen
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import com.example.firebaseloginmodule.FirebaseLoginEntry

/**
 * Main Navigation Graph for the app.
 * Uses Kotlin Serialization typed routes.
 *
 * IMPORTANT:
 * - ReminderViewModel is SHARED across HomeScreen and AddEditReminderScreen
 *   by scoping it to HomeGraphRoute.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: Any
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        // ------------------------------------------------------------
        // 🔐 LOGIN SCREEN
        // ------------------------------------------------------------
        composable<LoginRoute> {
            FirebaseLoginEntry(
                onLoginSuccess = {
                    navController.navigate(HomeGraphRoute) {
                        // Remove login from backstack
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                }
            )
        }

        // ------------------------------------------------------------
        // ⭐ SHARED REMINDER ROOT GRAPH
        // Everything inside this navigation block shares SAME ReminderViewModel
        // by using navController.getBackStackEntry(HomeGraphRoute)
        // ------------------------------------------------------------
        navigation<HomeGraphRoute>(
            startDestination = HomeRoute
        ) {

            // --------------------------------------------------------
            // 🏠 HOME SCREEN (Uses shared ReminderViewModel)
            // --------------------------------------------------------
            composable<HomeRoute> {
                // Get graph-level ViewModelStoreOwner
                val parentEntry = navController.getBackStackEntry(HomeGraphRoute)

                // Create/Get the shared ViewModel
                val sharedVm: ReminderViewModel = hiltViewModel(parentEntry)

                HomeScreen(
                    navController = navController,
                    reminderVm = sharedVm
                )
            }

            // --------------------------------------------------------
            // ✏️ ADD / EDIT REMINDER SCREEN (Uses shared ReminderViewModel)
            // --------------------------------------------------------
            composable<AddEditReminderRoute> { backStackEntry ->
                val args = backStackEntry.toRoute<AddEditReminderRoute>()

                // Same shared VM as HomeScreen
                val parentEntry = navController.getBackStackEntry(HomeGraphRoute)
                val sharedReminderVm: ReminderViewModel = hiltViewModel(parentEntry)

                AddEditReminderScreen(
                    navController = navController,
                    eventId = args.eventId,   // null => Add mode, non-null => Edit mode
                    reminderVm = sharedReminderVm
                )
            }
        }

        // ------------------------------------------------------------
        // 🎨 Pixel Card Editor Screen
        // ------------------------------------------------------------
        composable<PixelPreviewRoute> { entry ->
            val args = entry.toRoute<PixelPreviewRoute>()
            CardEditorScreen(reminderId = args.reminderId)
        }

        // ------------------------------------------------------------
        // ⏰ REMINDER MANAGER SCREEN
        // ------------------------------------------------------------
        composable<ReminderManagerRoute> {
            ReminderManagerScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
