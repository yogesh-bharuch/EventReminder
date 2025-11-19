package com.example.firebaseloginmodule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsible for handling login-related logic using FirebaseAuth.
 * Supports email/password login, sign-up, password reset, and toggling between modes.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    // Backing state for UI to observe
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    /**
     * Toggles between Sign In and Sign Up modes.
     * Useful for switching the UI form dynamically.
     */
    fun toggleAuthMode() {
        _uiState.value = _uiState.value.copy(
            isSignIn = !_uiState.value.isSignIn,
            error = null,
            isSuccess = false,
            isResetEmailSent = false
        )
    }

    /**
     * Authenticates the user using email and password.
     * If in sign-in mode, attempts to log in.
     * If in sign-up mode, attempts to register a new user.
     */
    fun authenticateWithEmail(email: String, password: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        val task = if (_uiState.value.isSignIn) {
            firebaseAuth.signInWithEmailAndPassword(email, password)
        } else {
            firebaseAuth.createUserWithEmailAndPassword(email, password)
        }

        task.addOnCompleteListener { result ->
            _uiState.value = if (result.isSuccessful) {
                _uiState.value.copy(isLoading = false, isSuccess = true)
            } else {
                _uiState.value.copy(isLoading = false, error = result.exception?.message)
            }
        }
    }

    /**
     * Sends a password reset email to the given address.
     * Only works if the email is registered with Firebase.
     */
    fun sendPasswordReset(email: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        firebaseAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                _uiState.value = if (task.isSuccessful) {
                    _uiState.value.copy(isLoading = false, isResetEmailSent = true)
                } else {
                    _uiState.value.copy(isLoading = false, error = task.exception?.message)
                }
            }
    }

    fun showError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
    }

    /**
     * Placeholder for phone number login.
     * Will be implemented with OTP verification in a future step.
     */
    fun startPhoneAuth(phoneNumber: String) {
        // TODO: Implement phone number authentication with verification callbacks
    }
}