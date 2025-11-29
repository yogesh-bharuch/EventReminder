package com.example.eventreminder.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.eventreminder.cards.ui.CardScreen
import com.example.eventreminder.cards.ui.pixel.PixelCardPreviewScreen
import com.example.eventreminder.ui.debug.CardDebugScreen
import com.example.firebaseloginmodule.FirebaseLoginEntry
import com.example.eventreminder.ui.screens.HomeScreen
import com.example.eventreminder.ui.screens.AddEditReminderScreen
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

        // PixelPreviewRoute
        composable<PixelPreviewRoute> {
            PixelCardPreviewScreen()
        }


        // ⏰ Reminder manager
        composable<ReminderManagerRoute> {
            ReminderManagerScreen(onBack = { navController.popBackStack() })
        }

        // 🧪 Card Debug / Developer Tools
        composable<CardDebugRoute> { entry ->
            val args = entry.toRoute<CardDebugRoute>()

            CardDebugScreen(
                //navController = navController,
                reminderId = args.reminderId,
                eventType = args.eventType
            )
        }


        // 🎉 Event entry (add/edit)
        composable<AddEditReminderRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<AddEditReminderRoute>()

            AddEditReminderScreen(
                navController = navController,
                eventId = args.eventId
            )
        }

        // 🎨 FINAL USER CARD SCREEN
        composable<CardRoute> { entry ->
            val args = entry.toRoute<CardRoute>()

            CardScreen(
                reminderId = args.reminderId
            )
        }
    }
}
