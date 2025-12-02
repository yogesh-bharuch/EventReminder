package com.example.eventreminder.cards.pixelcanvas.editor

import com.example.eventreminder.cards.pixelcanvas.CardDataPx
import com.example.eventreminder.cards.pixelcanvas.CardSpecPx

/**
 * TouchAvatarUtils
 *
 * Hit-testing utilities for FREE-AVATAR.
 * Determines whether a touch point falls inside the avatar circle.
 */
object TouchAvatarUtils {

    /**
     * Returns true if (touchX, touchY) lies inside the avatar's circular region.
     */
    fun isTouchInsideAvatar(
        touchX: Float,
        touchY: Float,
        spec: CardSpecPx,
        data: CardDataPx
    ): Boolean {

        val baseSize = (data.avatarBitmap?.width?.toFloat()
            ?: (spec.widthPx.toFloat() * 0.20f))

        val scale = data.avatarTransform.scale.coerceIn(0.1f, 8f)

        val centerX = spec.widthPx * data.avatarTransform.xNorm
        val centerY = spec.heightPx * data.avatarTransform.yNorm

        val radius = (baseSize * scale) / 2f

        val dx = touchX - centerX
        val dy = touchY - centerY

        return (dx * dx + dy * dy) <= (radius * radius)
    }
}