package com.example.eventreminder.cards.model

// =============================================================
// Simple model classes used by UI
// =============================================================
import androidx.annotation.DrawableRes
import java.util.concurrent.atomic.AtomicLong
import java.time.ZoneId

private val STICKER_ID_GENERATOR = AtomicLong(1000L)

// CardSticker — mutable fields (x/y/scale/rotation) are used by UI
data class CardSticker(
    val id: Long = STICKER_ID_GENERATOR.incrementAndGet(),
    @DrawableRes val drawableResId: Int? = null,
    var text: String? = null,
    var x: Float = 100f,
    var y: Float = 100f,
    var scale: Float = 1f,
    var rotation: Float = 0f
)

// ⬅⬅⬅ IMPORTANT: reminderId must now be String
data class CardData(
    val reminderId: String,                 // CHANGED
    val title: String,
    val name: String?,
    val eventKind: EventKind,
    val ageOrYearsLabel: String?,
    val originalDateLabel: String,
    val nextDateLabel: String,
    val timezone: ZoneId,
    val stickers: List<CardSticker>
)

// BackgroundItem — thumbnail item for packs
data class BackgroundItem(val name: String, @DrawableRes val resId: Int)

// CardBackground — persisted background representation
data class CardBackground(val id: String, val resId: Int)

// StickerItem model — small wrapper for pack entries
data class StickerItem(val id: String, val resId: Int? = null, val text: String? = null)

// EventKind
enum class EventKind { BIRTHDAY, ANNIVERSARY, GENERIC }
