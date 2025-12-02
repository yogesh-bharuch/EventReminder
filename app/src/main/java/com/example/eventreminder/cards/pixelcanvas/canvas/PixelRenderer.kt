package com.example.eventreminder.cards.pixelcanvas.canvas

// =============================================================
// PixelRenderer.kt
// MASTER RENDERER for the 1080×1200 Pixel Card
//
// Responsibilities:
//   • Clip to card shape
//   • Draw background (bitmap or gradient)
//   • Draw overlay tint
//   • Draw Title / Name / Age Circle / Date Row
//   • Draw Stickers
//   • Draw Avatar (free movable)
//   • Nothing else — no gesture logic, no hit-testing here
//
// All drawing steps are delegated to small modular files in this package.
// =============================================================

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx
import timber.log.Timber
import kotlin.math.min

private const val TAG = "PixelRenderer"

object PixelRenderer {

    // ---------------------------------------------------------
    // Utility paint factory (anti-alias enabled)
    // ---------------------------------------------------------
    private fun p() = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    // ---------------------------------------------------------
    // PUBLIC ENTRY FOR COMPOSE PREVIEW / PixelCanvas()
    // Scales the Android canvas to match the Compose box size.
    // ---------------------------------------------------------
    fun renderToDrawScope(
        ds: DrawScope,
        spec: CardSpecPx,
        data: CardDataPx
    ) {
        val canvas = ds.drawContext.canvas.nativeCanvas

        val scaleX = ds.size.width / spec.widthPx.toFloat()
        val scaleY = ds.size.height / spec.heightPx.toFloat()
        val scale = min(scaleX, scaleY)

        canvas.save()
        canvas.scale(scale, scale)

        try {
            renderToAndroidCanvas(canvas, spec, data)
        } finally {
            canvas.restore()
        }
    }

    // ---------------------------------------------------------
    // MAIN RENDERER — FINAL EXPORTED 1080×1200 PNG
    //
    // Order is very important:
    //   1) Clip
    //   2) Background (bitmap / gradient)
    //   3) Overlay tint
    //   4) Text layers (Title, Name, Age, Dates)
    //   5) Stickers
    //   6) Free Avatar
    //   7) Restore clip
    // ---------------------------------------------------------
    fun renderToAndroidCanvas(
        canvas: Canvas,
        spec: CardSpecPx,
        data: CardDataPx
    ) {
        Timber.tag(TAG).d("renderToAndroidCanvas START")

        // -----------------------------------------------------
        // (1) Clip to card rounded rectangle
        // -----------------------------------------------------
        canvas.save()
        val rectF = spec.backgroundLayer.toRectF()

        val clipPath = Path().apply {
            addRoundRect(
                rectF,
                spec.cornerRadiusPx.toFloat(),
                spec.cornerRadiusPx.toFloat(),
                Path.Direction.CW
            )
        }
        canvas.clipPath(clipPath)

        // -----------------------------------------------------
        // (2) Background (delegated)
        // -----------------------------------------------------
        DrawBackground.drawBackground(canvas, spec, data)

        // -----------------------------------------------------
        // (3) Overlay tint (soft dark glass)
        // Improves readability, adds professional depth.
        // -----------------------------------------------------
        canvas.drawRect(
            0f,
            0f,
            spec.widthPx.toFloat(),
            spec.heightPx.toFloat(),
            p().apply { color = Color.argb(18, 0, 0, 0) }
        )

        // -----------------------------------------------------
        // (4) Foreground text layers
        // -----------------------------------------------------
        DrawTitle.drawTitle(canvas, spec, data)
        DrawName.drawName(canvas, spec, data)
        DrawAgeCircle.drawAgeCircle(canvas, spec, data)
        DrawDateRow.drawDateRow(canvas, spec, data)

        // -----------------------------------------------------
        // (5) Stickers (all types)
        // -----------------------------------------------------
        DrawStickers.renderStickers(canvas, spec, data)

        // -----------------------------------------------------
        // (6) Avatar (free movable, circular mask)
        // -----------------------------------------------------
        DrawAvatarFree.drawAvatarFree(canvas, spec, data)

        // -----------------------------------------------------
        // Restore clip
        // -----------------------------------------------------
        canvas.restore()

        Timber.tag(TAG).d("renderToAndroidCanvas DONE")
    }
}
