package com.example.eventreminder.cards.pixelcanvas.pickers

import android.widget.Toast
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
import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.util.ImageUtil

/**
 * Row: Pick Background / Clear Background
 *
 * @param cardData Current card data (only used if you want future UI previews)
 * @param onBackgroundChanged Called with the loaded bitmap or null (to clear)
 * @param onPickBackground Triggered when user presses "Pick Bg". The parent
 *        (CardEditorScreen) owns the launcher so it handles permissions and SAF.
 */
@Composable
fun BackgroundPickerRow(
    cardData: CardDataPx,
    onBackgroundChanged: (android.graphics.Bitmap?) -> Unit,
    onPickBackground: () -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Pick background (delegates launcher to parent)
        Button(onClick = { onPickBackground() }) {
            Text("Pick Bg")
        }

        // Clear background
        Button(onClick = { onBackgroundChanged(null) }) {
            Text("Clear Bg")
        }
    }
}
