package com.example.eventreminder.ui.screens

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eventreminder.logging.AUTH_STATE_TAG
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * EmailVerificationScreen
 *
 * Shown when:
 * - User is authenticated
 * - BUT email is not verified
 *
 * Responsibilities:
 * - Inform user clearly
 * - Allow resend verification email
 * - Allow refresh / re-check verification
 * - Allow logout
 *
 * Rules:
 * - NO database access
 * - NO sync
 * - FirebaseAuth allowed here
 * - Logging rule enforced:
 *   [EmailVerificationScreen.kt::FunctionName]
 */
@Composable
fun EmailVerificationScreen(
    onVerified: () -> Unit,
    onLogout: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val user = auth.currentUser

    val scope = rememberCoroutineScope()
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Timber.tag(AUTH_STATE_TAG)
        .d(
            "Screen composed user=${user?.email} " +
                    "[EmailVerificationScreen.kt::EmailVerificationScreen]"
        )

    // ------------------------------------------------------------
    // SAFETY: If user becomes null, force logout
    // ------------------------------------------------------------
    LaunchedEffect(user) {
        if (user == null) {
            Timber.tag(AUTH_STATE_TAG)
                .w(
                    "User became null → force logout " +
                            "[EmailVerificationScreen.kt::LaunchedEffect]"
                )
            onLogout()
        }
    }

    // ------------------------------------------------------------
    // UI
    // ------------------------------------------------------------
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text(
                text = "Verify your email",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = user?.email ?: "Unknown email",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Please verify your email address to continue using EventReminder.",
                style = MaterialTheme.typography.bodyMedium
            )

            infoMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ----------------------------------------------------
            // RESEND VERIFICATION EMAIL
            // ----------------------------------------------------
            Button(
                enabled = !isLoading && user != null,
                onClick = {
                    scope.launch {
                        isLoading = true
                        infoMessage = null

                        try {
                            Timber.tag(AUTH_STATE_TAG)
                                .i(
                                    "Resend verification requested " +
                                            "[EmailVerificationScreen.kt::onResend]"
                                )

                            user?.sendEmailVerification()?.await()

                            infoMessage = "Verification email sent. Please check your inbox."

                            Timber.tag(AUTH_STATE_TAG)
                                .i(
                                    "Verification email sent successfully " +
                                            "[EmailVerificationScreen.kt::onResend]"
                                )
                        } catch (t: Throwable) {
                            Timber.tag(AUTH_STATE_TAG)
                                .e(
                                    t,
                                    "Failed to send verification email " +
                                            "[EmailVerificationScreen.kt::onResend]"
                                )
                            infoMessage = "Failed to send verification email. Try again later."
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                Text("Resend verification email")
            }

            // ----------------------------------------------------
            // REFRESH / RE-CHECK VERIFICATION
            // ----------------------------------------------------
            OutlinedButton(
                enabled = !isLoading && user != null,
                onClick = {
                    scope.launch {
                        isLoading = true
                        infoMessage = null

                        try {
                            Timber.tag(AUTH_STATE_TAG)
                                .i(
                                    "Refresh verification requested " +
                                            "[EmailVerificationScreen.kt::onRefresh]"
                                )

                            user?.reload()?.await()

                            val refreshed = auth.currentUser

                            if (refreshed?.isEmailVerified == true) {
                                Timber.tag(AUTH_STATE_TAG)
                                    .i(
                                        "Email verified → proceed " +
                                                "[EmailVerificationScreen.kt::onRefresh]"
                                    )
                                onVerified()
                            } else {
                                infoMessage = "Email not verified yet."
                                Timber.tag(AUTH_STATE_TAG)
                                    .w(
                                        "Email still unverified " +
                                                "[EmailVerificationScreen.kt::onRefresh]"
                                    )
                            }
                        } catch (t: Throwable) {
                            Timber.tag(AUTH_STATE_TAG)
                                .e(
                                    t,
                                    "Failed to refresh verification state " +
                                            "[EmailVerificationScreen.kt::onRefresh]"
                                )
                            infoMessage = "Failed to refresh status. Try again."
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                Text("I’ve verified my email")
            }

            // ----------------------------------------------------
            // LOGOUT
            // ----------------------------------------------------
            TextButton(
                enabled = !isLoading,
                onClick = {
                    Timber.tag(AUTH_STATE_TAG)
                        .w(
                            "User chose logout " +
                                    "[EmailVerificationScreen.kt::onLogout]"
                        )

                    auth.signOut()
                    onLogout()
                }
            ) {
                Text("Logout")
            }
        }
    }
}
