package com.example.eventreminder.navigation

// =============================================================
// Imports
// =============================================================
import android.content.Context
import androidx.compose.runtime.*
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.example.eventreminder.cards.pixelcanvas.ui.CardEditorScreen
import com.example.eventreminder.ui.components.BatteryOptimizationDialog
import com.example.eventreminder.ui.screens.AddEditReminderScreen
import com.example.eventreminder.ui.screens.HomeScreen
import com.example.eventreminder.ui.screens.ReminderManagerScreen
import com.example.eventreminder.ui.screens.SplashScreen
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import com.example.firebaseloginmodule.FirebaseLoginEntry
import timber.log.Timber

private const val TAG = "AppNavGraph"

// =============================================================
// AppNavGraph
// =============================================================
@Composable
fun AppNavGraph(
    context: Context,
    navController: NavHostController,
    startDestination: Any = SplashRoute
) {
    Timber.tag(TAG).d("NavHost started at = $startDestination")

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        // =============================================================
        // SPLASH ROUTE
        // =============================================================
        composable<SplashRoute> {

            var showBatteryDialog by remember { mutableStateOf(false) }

            if (showBatteryDialog) {
                BatteryOptimizationDialog(
                    onContinue = {
                        showBatteryDialog = false
                        navController.navigate(route = SplashRoute) {
                            popUpTo(route = SplashRoute) { inclusive = true }
                        }
                    }
                )
            }

            SplashScreen(
                onNavigateToHome = {
                    Timber.tag(TAG).d("Splash → Navigate to HomeGraph")
                    navController.navigate(route = HomeGraphRoute) {
                        popUpTo(route = SplashRoute) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    Timber.tag(TAG).d("Splash → Navigate to LoginRoute")
                    navController.navigate(route = LoginRoute) {
                        popUpTo(route = SplashRoute) { inclusive = true }
                    }
                },
                onBatteryFixRequired = {
                    Timber.tag(TAG).w("Battery optimization is ON")
                    showBatteryDialog = true
                }
            )
        }

        // =============================================================
        // LOGIN ROUTE
        // =============================================================
        composable<LoginRoute> {

            FirebaseLoginEntry(
                onLoginSuccess = {
                    Timber.tag(TAG).d("Login successful → Navigating to HomeGraph")
                    navController.navigate(route = HomeGraphRoute) {
                        popUpTo(route = LoginRoute) { inclusive = true }
                    }
                }
            )
        }

        // =============================================================
        // HOME GRAPH
        // =============================================================
        navigation(
            startDestination = HomeRoute::class,
            route = HomeGraphRoute::class
        ) {

            // -------------------------------------------------------------
            // MUST be inside the navigation block AND a composable scope
            // -------------------------------------------------------------
            composable<HomeRoute> { backStackEntry ->

                // Shared VM for whole HomeGraph
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(HomeGraphRoute::class)
                }
                val sharedVm: ReminderViewModel = hiltViewModel(parentEntry)

                HomeScreen(
                    navController = navController,
                    reminderVm = sharedVm
                )
            }

            // -------------------------------------------------------------
            // ADD / EDIT SCREEN
            // -------------------------------------------------------------
            composable<AddEditReminderRoute> { backStackEntry ->

                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(HomeGraphRoute::class)
                }
                val sharedVm: ReminderViewModel = hiltViewModel(parentEntry)

                val args = backStackEntry.toRoute<AddEditReminderRoute>()

                AddEditReminderScreen(
                    navController = navController,
                    eventId = args.eventId,
                    reminderVm = sharedVm
                )
            }

            // -------------------------------------------------------------
            // PIXEL CANVAS (Long ID)
            // -------------------------------------------------------------
            composable<PixelPreviewRoute> { backStackEntry ->
                val args = backStackEntry.toRoute<PixelPreviewRoute>()
                CardEditorScreen(reminderId = args.reminderId)
            }

            // -------------------------------------------------------------
            // PIXEL CANVAS (UUID)
            // -------------------------------------------------------------
            composable<PixelPreviewRouteString> { backStackEntry ->
                val args = backStackEntry.toRoute<PixelPreviewRouteString>()
                CardEditorScreen(reminderId = args.reminderIdString)
            }
        }




        // =============================================================
        // REMINDER MANAGER ROUTE
        // =============================================================
        composable<ReminderManagerRoute> {
            ReminderManagerScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
