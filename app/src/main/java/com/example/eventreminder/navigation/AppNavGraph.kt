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
import com.example.eventreminder.debug.ui.SchedulingDebugScreen
import com.example.eventreminder.ui.components.BatteryOptimizationDialog
import com.example.eventreminder.ui.screens.AddEditReminderScreen
import com.example.eventreminder.ui.screens.HomeScreen
import com.example.eventreminder.ui.screens.ReminderManagerScreen
import com.example.eventreminder.ui.screens.SplashScreen
import com.example.eventreminder.ui.screens.EmailVerificationScreen
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
    Timber.tag(TAG).d("NavHost started at=$startDestination [AppNavGraph.kt::AppNavGraph]")

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
                    Timber.tag(TAG).i(
                        "Splash → HomeGraph [AppNavGraph.kt::SplashRoute]"
                    )
                    navController.navigate(route = HomeGraphRoute) {
                        popUpTo(route = SplashRoute) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    Timber.tag(TAG).i(
                        "Splash → LoginRoute [AppNavGraph.kt::SplashRoute]"
                    )
                    navController.navigate(route = LoginRoute) {
                        popUpTo(route = SplashRoute) { inclusive = true }
                    }
                },
                onNavigateToEmailVerification = {
                    Timber.tag(TAG).i(
                        "Splash → EmailVerificationRoute [AppNavGraph.kt::SplashRoute]"
                    )
                    navController.navigate(route = EmailVerificationRoute) {
                        popUpTo(route = SplashRoute) { inclusive = true }
                    }
                },
                onBatteryFixRequired = {
                    Timber.tag(TAG).w(
                        "Battery optimization detected [AppNavGraph.kt::SplashRoute]"
                    )
                    showBatteryDialog = true
                }
            )
        }

        // =============================================================
        // LOGIN ROUTE (External Login Module)
        // =============================================================
        composable<LoginRoute> {
            FirebaseLoginEntry(
                onLoginSuccess = {
                    Timber.tag(TAG).i("Login success → HomeGraph [AppNavGraph.kt::LoginRoute]")
                    navController.navigate(route = HomeGraphRoute) {
                        popUpTo(route = LoginRoute) { inclusive = true }
                    }
                }
            )
        }

        // =============================================================
        // EMAIL VERIFICATION ROUTE
        // =============================================================
        composable<EmailVerificationRoute> {
            EmailVerificationScreen(
                onVerified = {
                    Timber.tag(TAG).i(
                        "Email verified → HomeGraph [AppNavGraph.kt::EmailVerificationRoute]"
                    )
                    navController.navigate(route = HomeGraphRoute) {
                        popUpTo(route = EmailVerificationRoute) { inclusive = true }
                    }
                },
                onLogout = {
                    Timber.tag(TAG).i(
                        "Logout from verification → Login [AppNavGraph.kt::EmailVerificationRoute]"
                    )
                    navController.navigate(route = LoginRoute) {
                        popUpTo(0)
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

            composable<HomeRoute> { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(HomeGraphRoute::class)
                }
                val sharedVm: ReminderViewModel = hiltViewModel(parentEntry)

                HomeScreen(
                    navController = navController,
                    reminderVm = sharedVm
                )
            }

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

            composable<PixelPreviewRoute> { backStackEntry ->
                val args = backStackEntry.toRoute<PixelPreviewRoute>()
                CardEditorScreen(reminderId = args.reminderId)
            }

            composable<PixelPreviewRouteString> { backStackEntry ->
                val args = backStackEntry.toRoute<PixelPreviewRouteString>()
                CardEditorScreen(reminderId = args.reminderIdString)
            }

            composable<SchedulingDebugRoute> {
                SchedulingDebugScreen()
            }
        }

        // =============================================================
        // REMINDER MANAGER
        // =============================================================
        composable<ReminderManagerRoute> {
            ReminderManagerScreen(
                onBack = { navController.popBackStack() },
                onOpenDebug = { navController.navigate(SchedulingDebugRoute) }
            )
        }
    }
}
