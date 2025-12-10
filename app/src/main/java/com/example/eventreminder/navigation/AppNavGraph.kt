package com.example.eventreminder.navigation

// =============================================================
// Imports
// =============================================================
import android.content.Context
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.example.eventreminder.util.SessionPrefs
import com.example.firebaseloginmodule.FirebaseLoginEntry
import com.google.firebase.auth.FirebaseAuth

// =============================================================
// AppNavGraph
// =============================================================
@Composable
fun AppNavGraph(
    context: Context,
    navController: NavHostController,
    startDestination: Any = SplashRoute   // ⭐ AUTO STARTS FROM SPLASH
) {

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        // ------------------------------------------------------------
        // ⭐ SPLASH SCREEN ROUTE
        // ------------------------------------------------------------
        composable<SplashRoute> {

            var showBatteryDialog by remember { mutableStateOf(false) }

            if (showBatteryDialog) {
                BatteryOptimizationDialog(
                    onContinue = {
                        showBatteryDialog = false
                        navController.navigate(SplashRoute) {
                            popUpTo(SplashRoute) { inclusive = true }
                        }
                    }
                )
            }

            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(HomeGraphRoute) {
                        popUpTo(SplashRoute) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(LoginRoute) {
                        popUpTo(SplashRoute) { inclusive = true }
                    }
                },
                onBatteryFixRequired = {
                    showBatteryDialog = true
                }
            )
        }

        // ------------------------------------------------------------
        // LOGIN SCREEN
        // ------------------------------------------------------------
        composable<LoginRoute> {
            FirebaseLoginEntry(
                onLoginSuccess = {
                    val user = FirebaseAuth.getInstance().currentUser

                    user?.let { u ->
                        // 🔑 Persist login info before navigating
                        SessionPrefs.save(
                            context = context,
                            uid = u.uid,
                            email = u.email
                        )
                    }

                    // ✅ Navigate to Home after saving session
                    navController.navigate(HomeGraphRoute) {
                        popUpTo(LoginRoute) { inclusive = true }
                    }
                }
            )
        }

        // ------------------------------------------------------------
        // HOME GRAPH (Parent Navigation)
        // ------------------------------------------------------------
        navigation<HomeGraphRoute>(
            startDestination = HomeRoute
        ) {

            // HOME SCREEN
            composable<HomeRoute> { entry ->
                val sharedVm: ReminderViewModel =
                    hiltViewModel(viewModelStoreOwner = entry)

                HomeScreen(
                    navController = navController,
                    reminderVm = sharedVm
                )
            }

            // ADD / EDIT REMINDER
            composable<AddEditReminderRoute> { entry ->
                val args = entry.toRoute<AddEditReminderRoute>()
                val sharedVm: ReminderViewModel =
                    hiltViewModel(viewModelStoreOwner = entry)

                AddEditReminderScreen(
                    navController = navController,
                    eventId = args.eventId,
                    reminderVm = sharedVm
                )
            }
        }

        // PIXEL CANVAS (LONG ID)
        composable<PixelPreviewRoute> { entry ->
            val args = entry.toRoute<PixelPreviewRoute>()
            CardEditorScreen(reminderId = args.reminderId)
        }

        // PIXEL CANVAS (UUID)
        composable<PixelPreviewRouteString> { entry ->
            val args = entry.toRoute<PixelPreviewRouteString>()
            CardEditorScreen(reminderId = args.reminderIdString)
        }

        // REMINDER MANAGER
        composable<ReminderManagerRoute> {
            ReminderManagerScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}