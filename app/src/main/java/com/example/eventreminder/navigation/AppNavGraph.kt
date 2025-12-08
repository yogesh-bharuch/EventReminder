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

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: Any
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        composable<LoginRoute> {
            FirebaseLoginEntry(
                onLoginSuccess = {
                    navController.navigate(HomeGraphRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                }
            )
        }

        navigation<HomeGraphRoute>(
            startDestination = HomeRoute
        ) {

            composable<HomeRoute> {
                val parentEntry = navController.getBackStackEntry(HomeGraphRoute)
                val sharedVm: ReminderViewModel = hiltViewModel(parentEntry)

                HomeScreen(
                    navController = navController,
                    reminderVm = sharedVm
                )
            }

            composable<AddEditReminderRoute> { backStackEntry ->
                val args = backStackEntry.toRoute<AddEditReminderRoute>()

                val parentEntry = navController.getBackStackEntry(HomeGraphRoute)
                val sharedReminderVm: ReminderViewModel = hiltViewModel(parentEntry)

                AddEditReminderScreen(
                    navController = navController,
                    eventId = args.eventId,
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
        // 🎨 idString Parallel PixelPreviewRoute
        // ------------------------------------------------------------
        composable<PixelPreviewRouteString> { entry ->                        // idchanged to idstring
            val args = entry.toRoute<PixelPreviewRouteString>()               // idchanged to idstring
            CardEditorScreen(reminderId = args.reminderIdString)        // idchanged to idstring
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
