package com.example.eventreminder.cards.pixelcanvas.pickers

// =============================================================
// AvatarPickerRow — Pick/Clear avatar photo
// - Pure UI module, controlled by ViewModel
// =============================================================

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.eventreminder.cards.CardViewModel

@Composable
fun AvatarPickerRow(
    viewModel: CardViewModel
) {
    val context = LocalContext.current

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onPixelAvatarImageSelected(context, uri)
        }
    }

    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = {
            avatarPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }) {
            Text("Pick Photo")
        }

        Button(onClick = { viewModel.clearPixelAvatar() }) {
            Text("Clear Photo")
        }
    }
}
