package com.example.eventreminder.cards.ui.pixel

// =============================================================
// PixelRendererTestScreen.kt (updated for 1080x1200)
// Advanced test screen with actions and tall preview
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

private const val TAG = "PixelRendererTestScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelRendererTestScreen() {

    Timber.tag(TAG).d("PixelRendererTestScreen Loaded")

    val spec = remember { CardSpecPx.default1080x1200() }

    var data by remember {
        mutableStateOf(
            CardDataPx(
                reminderId = 99L,
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
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PixelRenderer — Test Screen") })
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

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    // add many stickers (normalized random-ish positions)
                    val s = List(40) { index ->
                        StickerPx(
                            id = index.toLong(),
                            drawableResId = null,
                            bitmap = null,
                            text = "S$index",
                            xNorm = (0.1f + (index % 10) * 0.08f).coerceIn(0.05f, 0.95f),
                            yNorm = (0.2f + (index / 10) * 0.12f).coerceIn(0.1f, 0.9f),
                            scale = 1f,
                            rotationDeg = ((-30..30).random()).toFloat()
                        )
                    }
                    data = data.copy(stickers = s)
                    Timber.tag(TAG).d("Added ${s.size} stickers")
                }) {
                    Text("Add 40 Stickers")
                }

                Button(onClick = {
                    // sample: set a demo avatar from app resources if you have one
                    Timber.tag(TAG).d("No-op test button")
                }) {
                    Text("Test Render")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Tall pixel renderer output (1080×1200)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .aspectRatio(1080f / 1200f)
                    .background(Color.LightGray)
            ) {
                PixelCanvas(
                    spec = spec,
                    data = data,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
