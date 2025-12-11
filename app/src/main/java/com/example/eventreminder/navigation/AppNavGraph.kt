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

    Timber.tag("TRACE").e("🔄 NavHost composed → start=$startDestination")

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        // =============================================================
        // SPLASH ROUTE
        // =============================================================
        composable<SplashRoute> {

            Timber.tag("TRACE").e("📍 Composing → SplashRoute")

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
                    Timber.tag(TAG).i("Splash → HomeGraphRoute")
                    navController.navigate(route = HomeGraphRoute) {
                        popUpTo(route = SplashRoute) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    Timber.tag(TAG).i("Splash → LoginRoute")
                    navController.navigate(route = LoginRoute) {
                        popUpTo(route = SplashRoute) { inclusive = true }
                    }
                },
                onBatteryFixRequired = {
                    Timber.tag(TAG).w("Battery optimization detected")
                    showBatteryDialog = true
                }
            )
        }

        // =============================================================
        // LOGIN ROUTE
        // =============================================================
        composable<LoginRoute> {

            Timber.tag("TRACE").e("📍 Composing → LoginRoute")

            FirebaseLoginEntry(
                onLoginSuccess = {
                    Timber.tag(TAG).i("Firebase login success")

                    navController.navigate(route = HomeGraphRoute) {
                        popUpTo(route = LoginRoute) { inclusive = true }
                    }
                }
            )
        }

        // =============================================================
        // HOME GRAPH
        // =============================================================
        navigation<HomeGraphRoute>(
            startDestination = HomeRoute
        ) {

            Timber.tag("TRACE").e("📍 Composing → HomeGraphRoute")

            // ---------------- HOME SCREEN ----------------
            composable<HomeRoute> { entry ->
                Timber.tag("TRACE").e("📍 Composing → HomeRoute")

                val sharedVm: ReminderViewModel =
                    hiltViewModel(viewModelStoreOwner = entry)

                HomeScreen(
                    navController = navController,
                    reminderVm = sharedVm
                )
            }

            // ---------------- ADD / EDIT REMINDER ----------------
            composable<AddEditReminderRoute> { entry ->
                Timber.tag("TRACE").e("📍 Composing → AddEditReminderRoute")

                val args = entry.toRoute<AddEditReminderRoute>()
                val sharedVm: ReminderViewModel =
                    hiltViewModel(viewModelStoreOwner = entry)

                AddEditReminderScreen(
                    navController = navController,
                    eventId = args.eventId,
                    reminderVm = sharedVm
                )
            }

            // =============================================================
            // PIXEL CANVAS — LONG ID (inside HomeGraph)
            // =============================================================
            composable<PixelPreviewRoute> { entry ->
                Timber.tag("TRACE").e("📍 Composing → PixelPreviewRoute (LONG ID)")

                val args = entry.toRoute<PixelPreviewRoute>()
                CardEditorScreen(reminderId = args.reminderId)
            }

            // =============================================================
            // PIXEL CANVAS — UUID VERSION (inside HomeGraph)
            // =============================================================
            composable<PixelPreviewRouteString> { entry ->
                Timber.tag("TRACE").e("📍 Composing → PixelPreviewRouteString (UUID)")

                val args = entry.toRoute<PixelPreviewRouteString>()
                CardEditorScreen(reminderId = args.reminderIdString)
            }
        }

        // =============================================================
        // REMINDER MANAGER ROUTE
        // =============================================================
        composable<ReminderManagerRoute> {
            Timber.tag("TRACE").e("📍 Composing → ReminderManagerRoute")

            ReminderManagerScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
