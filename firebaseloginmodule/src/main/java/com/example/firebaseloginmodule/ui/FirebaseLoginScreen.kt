package com.example.firebaseloginmodule.ui

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
import com.example.firebaseloginmodule.LoginUiState
import com.example.firebaseloginmodule.LoginViewModel
import androidx.compose.foundation.background



@Composable
fun FirebaseLoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    // Trigger navigation once login is successful
    if (uiState.isSuccess) {
        LaunchedEffect(Unit) {
            onLoginSuccess()
        }
    }

    // Local state for user input
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // 🔥 background follows theme
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = if (uiState.isSignIn) "Sign In" else "Sign Up",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground  // theme-aware

        )

        Spacer(modifier = Modifier.height(24.dp))

        // Email input field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Password input field
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Forgot password button (only in Sign In mode)
        if (uiState.isSignIn) {
            TextButton(onClick = {
                if (email.isBlank()) {
                    viewModel.showError("Please enter your email to reset password.")
                } else {
                    viewModel.sendPasswordReset(email)
                }
            }) {
                Text("Forgot Password?",
                    color = MaterialTheme.colorScheme.primary  // theme-aware link color
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Submit button (Sign In or Sign Up)
        Button(
            onClick = {
                viewModel.authenticateWithEmail(email, password)
            },
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(if (uiState.isSignIn) "Sign In" else "Sign Up")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Toggle between Sign In and Sign Up
        TextButton(onClick = { viewModel.toggleAuthMode() }) {
            Text(
                if (uiState.isSignIn)
                    "Don't have an account? Sign Up"
                else
                    "Already have an account? Sign In",
                color = MaterialTheme.colorScheme.secondary // nicer for secondary action
            )
        }

        // Show loading indicator
        if (uiState.isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        // Show success message
        if (uiState.isSuccess) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Authentication successful!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }

        // Show error message
        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        // Show reset email confirmation
        if (uiState.isResetEmailSent) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Password reset email sent!", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}