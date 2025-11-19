package com.example.firebaseloginmodule

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.firebaseloginmodule.ui.FirebaseLoginScreen

/**
 * Entry point for host apps to embed the Firebase login screen.
 * Accepts a callback to notify the host app when login is successful.
 */
@Composable
fun FirebaseLoginEntry(
    onLoginSuccess: () -> Unit = {} // ✅ Optional callback for navigation
) {
    val viewModel: LoginViewModel = hiltViewModel()
    FirebaseLoginScreen(
        viewModel = viewModel,
        onLoginSuccess = onLoginSuccess
    )
}