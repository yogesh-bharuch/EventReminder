package com.example.firebaseloginmodule.ui

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.firebaseloginmodule.LoginViewModel
import com.example.firebaseloginmodule.LoginUiState

/**
 * FirebaseLoginScreen
 *
 * Responsibilities:
 * - Email / Password Sign In
 * - Email / Password Sign Up
 * - Password Reset
 * - Email Verification UX (post sign-up)
 *
 * Navigation rules:
 * - Navigate ONLY when uiState.isSuccess == true
 * - Sign-up NEVER auto-navigates (email verification required)
 *
 * Logging:
 * - LoginViewModel owns logging
 */
@Composable
fun FirebaseLoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit = {}
) {

    // ------------------------------------------------------------
    // STATE
    // ------------------------------------------------------------
    val uiState by viewModel.uiState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val context = LocalContext.current

    // ------------------------------------------------------------
    // NAVIGATION (SIGN-IN SUCCESS ONLY)
    // ------------------------------------------------------------
    if (uiState.isSuccess) {
        LaunchedEffect(Unit) {
            onLoginSuccess()
        }
    }

    // ------------------------------------------------------------
    // UI
    // ------------------------------------------------------------
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // --------------------------------------------------------
        // TITLE
        // --------------------------------------------------------
        Text(
            text = if (uiState.isSignIn) "Sign In" else "Sign Up",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --------------------------------------------------------
        // EMAIL INPUT
        // --------------------------------------------------------
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --------------------------------------------------------
        // PASSWORD INPUT
        // --------------------------------------------------------
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --------------------------------------------------------
        // FORGOT PASSWORD (SIGN-IN ONLY)
        // --------------------------------------------------------
        if (uiState.isSignIn) {
            TextButton(
                onClick = {
                    if (email.isBlank()) {
                        viewModel.showError("Please enter your email to reset password.")
                    } else {
                        viewModel.sendPasswordReset(email)
                    }
                }
            ) {
                Text(
                    text = "Forgot Password?",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --------------------------------------------------------
        // SUBMIT BUTTON
        // --------------------------------------------------------
        Button(
            onClick = {
                viewModel.authenticateWithEmail(
                    email = email.trim(),
                    password = password
                )
            },
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (uiState.isSignIn) "Sign In" else "Sign Up")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --------------------------------------------------------
        // TOGGLE SIGN-IN / SIGN-UP
        // --------------------------------------------------------
        TextButton(
            onClick = {
                password = ""
                viewModel.toggleAuthMode()
            }
        ) {
            Text(
                text =
                    if (uiState.isSignIn)
                        "Don't have an account? Sign Up"
                    else
                        "Already have an account? Sign In",
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // --------------------------------------------------------
        // LOADING INDICATOR
        // --------------------------------------------------------
        if (uiState.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        // --------------------------------------------------------
        // EMAIL VERIFICATION SENT (SIGN-UP FLOW)
        // --------------------------------------------------------
        if (uiState.isEmailVerificationSent) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Verification email sent. Please verify your email before logging in.",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // --------------------------------------------------------
        // PASSWORD RESET CONFIRMATION
        // --------------------------------------------------------
        if (uiState.isResetEmailSent) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Password reset email sent!",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // --------------------------------------------------------
        // ERROR MESSAGE
        // --------------------------------------------------------
        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
