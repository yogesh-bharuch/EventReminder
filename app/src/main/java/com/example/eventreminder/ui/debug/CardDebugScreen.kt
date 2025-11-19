package com.example.eventreminder.ui.debug

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import timber.log.Timber
import android.net.Uri

// Project functions
import com.example.eventreminder.card.createBlankCardTest
import com.example.eventreminder.card.render.CardRenderer
import com.example.eventreminder.card.sample.CardSampleData
import com.example.eventreminder.card.theme.ThemeEngine

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardDebugScreen"

/**
 * CardDebugScreen
 *
 * Developer Tools screen for testing Card Generator features.
 * This screen will host debug buttons for each TODO milestone.
 */
@Composable
fun CardDebugScreen(
    navController: NavController
) {
    // =============================================================
    // Setup
    // =============================================================
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val lastSavedCardUri = rememberSaveable { mutableStateOf<String?>(null) }
    var themeTestIndex by rememberSaveable { mutableStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }

    // =============================================================
    // Scaffold
    // =============================================================
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->

        // =============================================================
        // Content
        // =============================================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            Text(
                text = "Card Generator — Developer Tools",
                style = MaterialTheme.typography.headlineSmall
            )

            // =============================================================
            // TODO-0 — Create Blank Card Test
            // =============================================================
            Button(
                onClick = {
                    scope.launch {
                        val uri = createBlankCardTest(context)

                        if (uri != null) {
                            lastSavedCardUri.value = uri.toString()
                            Timber.tag(TAG).d("Card test created: $uri")
                            snackbarHostState.showSnackbar("Card test saved!")
                        } else {
                            Timber.tag(TAG).e("Card test failed")
                            snackbarHostState.showSnackbar("Failed to generate test card.")
                        }
                    }
                }
            ) {
                Text("Run Card Test (TODO-0)")
            }

            // =============================================================
            // TODO-0 — Open Last Saved Card
            // =============================================================
            OutlinedButton(
                onClick = {
                    val uriString = lastSavedCardUri.value

                    if (uriString == null) {
                        scope.launch { snackbarHostState.showSnackbar("No saved card to open.") }
                        Timber.tag(TAG).w("Open Card: No URI available")
                        return@OutlinedButton
                    }

                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(uriString), "image/*")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                        Timber.tag(TAG).d("Opening saved card: $uriString")

                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Failed to open card")
                        scope.launch { snackbarHostState.showSnackbar("Unable to open saved card.") }
                    }
                }
            ) {
                Text("Open Last Saved Card")
            }

            // =============================================================
            // TODO-1 — Sample Data Models
            // =============================================================
            OutlinedButton(
                onClick = {
                    val sample = CardSampleData.sampleBirthdayForMom()
                    Timber.tag(TAG).d("Sample Data Model:\n$sample")

                    scope.launch {
                        snackbarHostState.showSnackbar("Sample model logged")
                    }
                }
            ) {
                Text("Test Data Models (TODO-1)")
            }

            // =============================================================
            // TODO-2 — Cycle Themes
            // =============================================================
            OutlinedButton(
                onClick = {
                    themeTestIndex = (themeTestIndex + 1) % ThemeEngine.allThemes.size
                    val theme = ThemeEngine.allThemes[themeTestIndex]

                    Timber.tag(TAG).d("ThemeEngine Test: ${theme.themeName}")

                    scope.launch {
                        snackbarHostState.showSnackbar("Theme: ${theme.themeName}")
                    }
                }
            ) {
                Text("Cycle Themes (TODO-2)")
            }

            // =============================================================
            // TODO-2 — Theme Preview Box
            // =============================================================
            val previewTheme = ThemeEngine.allThemes[themeTestIndex]

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(top = 8.dp)
                    .background(previewTheme.backgroundColor)
            ) {
                previewTheme.gradientOverlay?.let { gradient ->
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(gradient)
                    )
                }

                Text(
                    text = previewTheme.themeName,
                    color = previewTheme.accentColor,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // =============================================================
            // TODO-3 — Render Simple Card
            // =============================================================
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val sampleReq = CardSampleData.sampleBirthdayForMom()
                        Timber.tag(TAG).d("TODO-3: Rendering sample card…")

                        val savedUri = CardRenderer.renderAndSaveToGallery(context, sampleReq)

                        if (savedUri != null) {
                            lastSavedCardUri.value = savedUri.toString()
                            Timber.tag(TAG).d("TODO-3: Saved: $savedUri")
                            snackbarHostState.showSnackbar("Rendered card saved!")
                        } else {
                            Timber.tag(TAG).e("TODO-3 failed")
                            snackbarHostState.showSnackbar("Render failed.")
                        }
                    }
                }
            ) {
                Text("Render Simple Card (TODO-3)")
            }

            // =============================================================
            // TODO-5 — Render Card WITH Photo
            // =============================================================
            OutlinedButton(
                onClick = {
                    scope.launch {

                        Timber.tag(TAG).d("TODO-5: Starting Render With Photo Layer")

                        val sample = CardSampleData.sampleBirthdayForMom()

                        // Set a sample photo from drawable
                        val sampleUri = Uri.parse(
                            "android.resource://${context.packageName}/${android.R.drawable.ic_menu_camera}"
                        )

                        // Immutable-safe update using copy()
                        val updatedSample = sample.copy(
                            photoLayer = sample.photoLayer?.copy(
                                photoUri = sampleUri
                            )
                        )

                        // Important: use updatedSample
                        val savedUri = CardRenderer.renderAndSaveToGallery(context, updatedSample)

                        if (savedUri != null) {
                            lastSavedCardUri.value = savedUri.toString()

                            Timber.tag(TAG).d("TODO-5: Photo card saved: $savedUri")
                            snackbarHostState.showSnackbar("Card with photo saved!")
                        } else {
                            Timber.tag(TAG).e("TODO-5: render/save failed")
                            snackbarHostState.showSnackbar("Failed to render card with photo.")
                        }
                    }
                }
            ) {
                Text("Render Card With Photo (TODO-5)")
            }
        }
    }
}
