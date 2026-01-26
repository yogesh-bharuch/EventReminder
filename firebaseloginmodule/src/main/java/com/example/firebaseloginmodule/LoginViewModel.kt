package com.example.firebaseloginmodule

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    fun toggleAuthMode() {
        _uiState.value = _uiState.value.copy(
            isSignIn = !_uiState.value.isSignIn,
            error = null,
            isSuccess = false,
            isResetEmailSent = false,
            isEmailVerificationSent = false
        )
    }

    /**
     * EMAIL AUTH (SIGN IN + SIGN UP)
     *
     * RULES:
     * - SignUp → send verification + force logout
     * - SignIn → block if email not verified
     */
    fun authenticateWithEmail(email: String, password: String) {

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null
        )

        if (_uiState.value.isSignIn) {

            // ===============================
            // SIGN IN FLOW
            // ===============================
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { result ->

                    val user = firebaseAuth.currentUser

                    _uiState.value =
                        if (result.isSuccessful && user != null) {

                            if (!user.isEmailVerified) {
                                firebaseAuth.signOut()

                                _uiState.value.copy(
                                    isLoading = false,
                                    error = "Please verify your email before logging in."
                                )
                            } else {
                                _uiState.value.copy(
                                    isLoading = false,
                                    isSuccess = true
                                )
                            }

                        } else {
                            _uiState.value.copy(
                                isLoading = false,
                                error = result.exception?.message
                            )
                        }
                }

        } else {

            // ===============================
            // SIGN UP FLOW
            // ===============================
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { result ->

                    val user = firebaseAuth.currentUser

                    if (result.isSuccessful && user != null) {

                        user.sendEmailVerification()
                            .addOnCompleteListener {

                                // 🚫 DO NOT auto-login
                                firebaseAuth.signOut()

                                _uiState.value = _uiState.value.copy(
                                    isLoading = false,
                                    isEmailVerificationSent = true
                                )
                            }

                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.exception?.message
                        )
                    }
                }
        }
    }

    fun sendPasswordReset(email: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        firebaseAuth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                _uiState.value =
                    if (task.isSuccessful) {
                        _uiState.value.copy(
                            isLoading = false,
                            isResetEmailSent = true
                        )
                    } else {
                        _uiState.value.copy(
                            isLoading = false,
                            error = task.exception?.message
                        )
                    }
            }
    }

    /**
     * UI helper to surface validation errors.
     */
    fun showError(message: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = message
        )
    }
}
