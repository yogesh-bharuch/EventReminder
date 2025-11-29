package com.example.eventreminder.cards.ui.pixel

// =============================================================
// PixelRendererSimpleTestScreen.kt (updated for 1080x1200)
// Shows tall canvas with gradient background
// =============================================================

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.eventreminder.cards.pixel.*
import timber.log.Timber

private const val TAG = "PixelRendererSimpleTest"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelRendererSimpleTestScreen() {

    Timber.tag(TAG).d("Simple PixelRenderer Test Screen Loaded")

    // canonical spec now 1080 x 1200
    val spec = remember { CardSpecPx.default1080x1200() }

    val data = remember {
        CardDataPx(
            reminderId = 1L,
            titleText = "Happy Birthday",
            nameText = "Yogesh",
            showTitle = true,
            showName = true,
            avatarBitmap = null,
            backgroundBitmap = null,
            stickers = emptyList(),
            originalDateLabel = "Jan 1, 1990",
            nextDateLabel = "Fri, Jan 1, 2026",
            ageOrYearsLabel = "34"
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Pixel Renderer — Baseline Test") })
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFEFEFEF)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Below is the PixelRenderer output (scaled).",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(20.dp))

            // tall preview with small horizontal margins (8dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .aspectRatio(1080f / 1200f)   // use new ratio
                    .background(Color.LightGray)
            ) {
                PixelCanvas(
                    spec = spec,
                    data = data,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
