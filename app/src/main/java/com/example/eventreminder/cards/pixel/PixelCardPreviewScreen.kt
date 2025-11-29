package com.example.eventreminder.cards.ui.pixel

// =============================================================
// PixelCardPreviewScreen.kt
// A standalone screen to preview PixelRenderer-based card output.
// Safe to call from HomeScreen or any NavGraph.
//
// This screen does NOT interfere with the existing CardScreen.
// =============================================================

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eventreminder.cards.pixel.CardDataPx
import com.example.eventreminder.cards.pixel.CardSpecPx
import com.example.eventreminder.cards.pixel.PixelCanvas
import timber.log.Timber

private const val TAG = "PixelCardPreviewScreen"

/**
 * Navigation-safe, production-ready preview screen for pixel renderer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelCardPreviewScreen() {
    Timber.tag(TAG).d("PixelCardPreviewScreen → render")

    // ---------------------------------------------------------
    // Spec: canonical 1080 x 720
    // ---------------------------------------------------------
    val spec = remember { CardSpecPx.default1080x720() }

    // ---------------------------------------------------------
    // Dummy pixel card data (replace with actual ViewModel later)
    // ---------------------------------------------------------
    val dummyData = remember {
        CardDataPx(
            reminderId = 999L,
            titleText = "Happy Birthday",
            nameText = "Yogesh",
            showTitle = true,
            showName = true,
            avatarBitmap = null,                 // no avatar for now
            backgroundBitmap = null,             // no background for now
            stickers = emptyList(),
            originalDateLabel = "Jan 1, 1990",
            nextDateLabel = "Fri, Jan 1, 2026",
            ageOrYearsLabel = "36"
        )
    }

    // ---------------------------------------------------------
    // UI Layout
    // ---------------------------------------------------------
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pixel Card Preview") })
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {

            Text(
                text = "Canonical 1080×720px renderer output\nScaled to fit container.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(12.dp))

            // -------------------------------------------------
            // PixelCanvas Composable
            // Scales the 1080×720 canvas proportionally
            // -------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)    // roughly matches old card preview height
            ) {
                PixelCanvas(
                    spec = spec,
                    data = dummyData,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "This is using the new Pixel Renderer.\n" +
                        "Next steps: integrate ViewModel & actual data.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
