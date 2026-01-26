package com.example.firebaseloginmodule

/**
 * Represents the current state of the login UI.
 */
data class LoginUiState(
    val isSignIn: Boolean = true,           // true = sign in, false = sign up
    val isLoading: Boolean = false,         // shows progress indicator
    val isSuccess: Boolean = false,         // true if login/signup succeeded
    // 🔥 NEW — Email verification flow
    val isEmailVerificationSent: Boolean = false,

    val isResetEmailSent: Boolean = false,  // true if forgot password email sent
    val error: String? = null               // error message to display
)