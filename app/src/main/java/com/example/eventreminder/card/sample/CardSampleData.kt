package com.example.eventreminder.card.sample

// =============================================================
// Imports
// =============================================================
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp
import com.example.eventreminder.card.model.*
import java.time.LocalDate
import timber.log.Timber

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardSampleData"

/**
 * CardSampleData
 *
 * Generates sample card data for testing the rendering engine
 * inside CardDebugScreen before hooking real database events.
 *
 * All sample requests now include:
 * - text blocks
 * - theme
 * - relation icon
 * - full photoLayer (to support TODO-5 photo rendering)
 */
object CardSampleData {

    // =============================================================
    // Sample Birthday Card — “Mom”
    // =============================================================
    fun sampleBirthdayForMom(): CardRenderRequest {

        Timber.tag(TAG).d("Generating sampleBirthdayForMom()")

        // Canvas dimensions (CardRenderRequest default uses 1080x1080)
        val canvasWidth = 1080f

        // ---------------------------------------------------------
        // THEME
        // ---------------------------------------------------------
        val theme = CardTheme(
            backgroundColor = Color(0xFFFFE6F1),
            gradientOverlay = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFAACC),
                    Color(0x00FFAACC)
                ),
                tileMode = TileMode.Clamp
            ),
            accentColor = Color(0xFFD81B60),
            secondaryAccentColor = Color(0xFFFFCDD2),
            themeName = "Mom Pink Delight"
        )

        // ---------------------------------------------------------
        // AGE INFO
        // ---------------------------------------------------------
        val age = AgeInfo(eventDate = LocalDate.parse("1980-11-20"))

        // ---------------------------------------------------------
        // TEXT BLOCKS
        // ---------------------------------------------------------
        val textBlocks = listOf(

            // =============================================================
            // Main Greeting (Top Title)
            // =============================================================
            CardTextBlock(
                text = "Happy Birthday!",
                fontSize = 52.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = theme.accentColor,
                x = canvasWidth / 2f,          // center X
                y = 80f,                       // TOP POSITION
                maxWidth = canvasWidth * 0.80f,
                alignment = TextAlignment.Center,
                autoFit = true                // static, spacing controlled manually
            ),

            // =============================================================
            // Name block (Large Center Text)
            // =============================================================
            CardTextBlock(
                text = "Mom ❤️",
                fontSize = 64.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                color = theme.accentColor,
                x = canvasWidth / 2f,          // center X
                y = 200f,                      // BELOW greeting (120px)
                maxWidth = canvasWidth * 0.70f,
                alignment = TextAlignment.Center,
                autoFit = true
            ),

            // =============================================================
            // Age line (Left aligned)
            // =============================================================
            CardTextBlock(
                text = "Turns ${age.years} Today!",
                fontSize = 68.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = Color(0xFFAD1457),
                x = canvasWidth * 0.10f,       // left margin
                y = 330f,                      // below name block
                maxWidth = canvasWidth * 0.80f,
                alignment = TextAlignment.Left,
                autoFit = true
            )
        )

        // ---------------------------------------------------------
        // RETURN SAMPLE REQUEST (with mandatory photoLayer)
        // ---------------------------------------------------------
        return CardRenderRequest(
            theme = theme,
            recipientName = "Mom",
            message = "Happy Birthday Mom!",
            ageInfo = age,
            relationIcon = RelationIcon(
                relationName = "Mother",
                drawableResId = android.R.drawable.star_big_on
            ),
            textBlocks = textBlocks,

            // ⭐ IMPORTANT: photoLayer MUST exist or TODO-5 cannot work
            photoLayer = CardPhotoLayer(
                photoUri = null,                                          // filled later via .copy() in Debug Screen
                cropType = CardPhotoLayer.CropType.Circle,                // default circle crop
                x = canvasWidth * 0.30f,                                  // centered horizontal
                y = 480f,                                                 // below text
                size = 380f,                                              // large portrait circle
                borderColor = Color.White.toArgb(),                       // optional
                borderWidth = 12f                                         // clean white border
            )
        )
    }

    // =============================================================
    // Sample Anniversary Card — “Parents”
    // =============================================================
    fun sampleAnniversaryForParents(): CardRenderRequest {

        Timber.tag(TAG).d("Generating sampleAnniversaryForParents()")

        val canvasWidth = 1080f

        // ---------------------------------------------------------
        // THEME
        // ---------------------------------------------------------
        val theme = CardTheme(
            backgroundColor = Color(0xFFE3F2FD),
            gradientOverlay = Brush.verticalGradient(
                listOf(Color(0xFF90CAF9), Color(0x0090CAF9))
            ),
            accentColor = Color(0xFF0D47A1),
            secondaryAccentColor = Color(0xFF64B5F6),
            themeName = "Elegant Blue"
        )

        // ---------------------------------------------------------
        // AGE INFO
        // ---------------------------------------------------------
        val age = AgeInfo(LocalDate.parse("1990-01-01"))

        // ---------------------------------------------------------
        // TEXT BLOCKS
        // ---------------------------------------------------------
        val textBlocks = listOf(

            CardTextBlock(
                text = "Happy Anniversary!",
                fontSize = 50.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = theme.accentColor,
                x = canvasWidth / 2f,
                y = 150f,
                maxWidth = canvasWidth * 0.80f,
                alignment = TextAlignment.Center,
                autoFit = true
            ),

            CardTextBlock(
                text = "Mom & Dad",
                fontSize = 60.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                color = theme.accentColor,
                x = canvasWidth / 2f,
                y = 250f,
                maxWidth = canvasWidth * 0.70f,
                alignment = TextAlignment.Center,
                autoFit = true
            ),

            CardTextBlock(
                text = "${age.years} Years Together ❤️",
                fontSize = 38.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = Color(0xFF1976D2),
                x = canvasWidth * 0.10f,
                y = 340f,
                maxWidth = canvasWidth * 0.80f,
                alignment = TextAlignment.Left,
                autoFit = false
            )
        )

        // ---------------------------------------------------------
        // RETURN REQUEST (also includes photoLayer)
        // ---------------------------------------------------------
        return CardRenderRequest(
            theme = theme,
            recipientName = "Parents",
            message = "Happy Anniversary!",
            ageInfo = age,
            relationIcon = RelationIcon(
                relationName = "Parents",
                drawableResId = android.R.drawable.ic_menu_gallery
            ),
            textBlocks = textBlocks,

            photoLayer = CardPhotoLayer(
                photoUri = null,
                cropType = CardPhotoLayer.CropType.Circle,
                x = canvasWidth * 0.30f,
                y = 480f,
                size = 360f,
                borderColor = Color.White.toArgb(),
                borderWidth = 12f
            )
        )
    }
}
