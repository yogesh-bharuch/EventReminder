package com.example.eventreminder.cards.capture

import android.content.Context
import android.graphics.Bitmap
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAG = "CardShareHelper"

object CardShareHelper {

    private fun timestamp() =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneId.systemDefault())
            .format(Instant.now())

    fun saveCard(context: Context, bitmap: Bitmap): String? {
        val name = "card_${timestamp()}.png"
        return BitmapSaver.saveToGallery(context, bitmap, name)
    }

    fun shareCard(context: Context, bitmap: Bitmap) {
        val name = "shared_card_${timestamp()}"
        BitmapSharer.share(context, bitmap, name)
    }
}
