package com.example.eventreminder.cards.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.eventreminder.cards.CardViewModel
import com.example.eventreminder.cards.state.CardUiState
import com.example.eventreminder.cards.capture.CaptureBox
import com.example.eventreminder.cards.capture.CaptureController
import com.example.eventreminder.cards.capture.CardShareHelper
import timber.log.Timber

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardScreen"

// =============================================================
// Pending Action (Save / Share)
// =============================================================
private enum class PendingAction {
    NONE, SAVE, SHARE
}

// =============================================================
// CardScreen (User-facing)
// =============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScreen(
    reminderId: Long,
    viewModel: CardViewModel = hiltViewModel()
) {
    Timber.tag(TAG).d("CardScreen → reminderId=$reminderId")

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val captureController = remember { CaptureController() }

    var latestBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingAction by remember { mutableStateOf(PendingAction.NONE) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text( "Event Card") })
        }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {

            when (uiState) {

                is CardUiState.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                is CardUiState.Error ->
                    Text(
                        text = (uiState as CardUiState.Error).message,
                        modifier = Modifier.align(Alignment.Center)
                    )

                is CardUiState.Placeholder ->
                    Text(
                        "No reminderId provided.",
                        modifier = Modifier.align(Alignment.Center)
                    )

                is CardUiState.Data -> {

                    val cardData = (uiState as CardUiState.Data).cardData

                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {

                        // ---------------------------------------------------------
                        // CARD + CAPTURE WRAPPER
                        // ---------------------------------------------------------
                        CaptureBox(
                            controller = captureController,
                            onCaptured = { bmp ->
                                Timber.tag(TAG).d("Captured bitmap: ${bmp.width}x${bmp.height}")
                                latestBitmap = bmp
                            }
                        ) {
                            CardPreview(
                                cardData = cardData,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // ---------------------------------------------------------
                        // ACTION BUTTONS
                        // ---------------------------------------------------------
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {

                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    pendingAction = PendingAction.SAVE
                                    captureController.capture()
                                }
                            ) {
                                Text("Save")
                            }

                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    pendingAction = PendingAction.SHARE
                                    captureController.capture()
                                }
                            ) {
                                Text("Share")
                            }
                        }
                    }
                }
            }
        }
    }

    // =============================================================
    // Handle actions AFTER bitmap is ready
    // =============================================================
    LaunchedEffect(latestBitmap) {

        val bmp = latestBitmap ?: return@LaunchedEffect

        when (pendingAction) {

            PendingAction.SAVE -> {
                val uri = CardShareHelper.saveCard(context, bmp)
                if (uri != null) {
                    Timber.tag(TAG).d("Saved card → $uri")
                } else {
                    Timber.tag(TAG).e("Failed to save card")
                }
            }

            PendingAction.SHARE -> {
                CardShareHelper.shareCard(context, bmp)
                Timber.tag(TAG).d("Share action triggered")
            }

            else -> Unit
        }

        // Reset states
        pendingAction = PendingAction.NONE
        latestBitmap = null
    }
}
