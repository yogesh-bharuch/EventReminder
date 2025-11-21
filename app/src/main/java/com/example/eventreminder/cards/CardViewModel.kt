package com.example.eventreminder.cards

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.cards.model.CardData
import com.example.eventreminder.cards.model.CardSticker
import com.example.eventreminder.cards.model.EventKind
import com.example.eventreminder.cards.model.StickerItem
import com.example.eventreminder.cards.state.CardUiState
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.model.ReminderTitle
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.util.NextOccurrenceCalculator
import com.example.eventreminder.cards.util.ImageUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.*
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardViewModel"

// =============================================================
// ViewModel
// =============================================================
@HiltViewModel
class CardViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val nextCalculator: NextOccurrenceCalculator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // ---------------------------------------------------------
    // UI State
    // ---------------------------------------------------------
    private val _uiState = MutableStateFlow<CardUiState>(CardUiState.Loading)
    val uiState: StateFlow<CardUiState> = _uiState.asStateFlow()

    // Avatar image state (in-memory + cached path)
    private val _avatarBitmap = MutableStateFlow<Bitmap?>(null)
    val avatarBitmap: StateFlow<Bitmap?> = _avatarBitmap.asStateFlow()

    private val _avatarPath = MutableStateFlow<String?>(null)
    val avatarPath: StateFlow<String?> = _avatarPath.asStateFlow()

    // ---------------------------------------------------------
    // Avatar transform state (so avatar can be moved/scaled like a sticker)
    // Mode A: only one avatar; transform preserved in VM while editing session.
    // Values are in dp for x/y offsets and scale as float.
    // ---------------------------------------------------------
    private val _avatarOffsetX = MutableStateFlow(220f)   // default inside-card left padding (dp)
    private val _avatarOffsetY = MutableStateFlow(24f)   // default top padding (dp)
    private val _avatarScale = MutableStateFlow(1.1f)
    private val _avatarRotation = MutableStateFlow(0f)

    val avatarOffsetX: StateFlow<Float> = _avatarOffsetX.asStateFlow()
    val avatarOffsetY: StateFlow<Float> = _avatarOffsetY.asStateFlow()
    val avatarScale: StateFlow<Float> = _avatarScale.asStateFlow()
    val avatarRotation: StateFlow<Float> = _avatarRotation.asStateFlow()

    // Typed navigation arg
    private val reminderIdArg: Long = savedStateHandle.get<Long>("reminderId") ?: -1L

    init {
        Timber.tag(TAG).d("CardViewModel init → reminderId=%d", reminderIdArg)
        if (reminderIdArg == -1L) {
            _uiState.value = CardUiState.Placeholder
        } else {
            loadReminder(reminderIdArg)
        }
    }

    // ---------------------------------------------------------
    // Existing sticker APIs (unchanged)
    // ---------------------------------------------------------
    fun addSticker(item: StickerItem) {
        val current = (_uiState.value as? CardUiState.Data)?.cardData ?: return

        val newSticker = CardSticker(
            drawableResId = item.resId,
            text = item.text,
            x = 100f,
            y = 100f,
            scale = 1f,
            rotation = 0f
        )

        Timber.tag(TAG).d("Adding sticker id=%S", newSticker.id)

        _uiState.value = CardUiState.Data(
            current.copy(stickers = current.stickers + newSticker)
        )
    }

    fun removeSticker(sticker: CardSticker) {
        val current = (_uiState.value as? CardUiState.Data)?.cardData ?: return

        Timber.tag(TAG).d("Removing sticker id=%S", sticker.id)

        _uiState.value = CardUiState.Data(
            current.copy(stickers = current.stickers.filter { it.id != sticker.id })
        )
    }

    fun updateSticker(sticker: CardSticker) {
        val current = (_uiState.value as? CardUiState.Data)?.cardData ?: return

        _uiState.value = CardUiState.Data(
            current.copy(
                stickers = current.stickers.map { if (it.id == sticker.id) sticker else it }
            )
        )
    }

    // ---------------------------------------------------------
    // Image / Avatar pipeline
    // ---------------------------------------------------------
    /**
     * After user selects an image Uri — load a downsampled bitmap for cropper/preview.
     */
    fun onImageUriSelected(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bmp = ImageUtil.loadBitmapFromUri(context, uri, maxDim = 1600)
                if (bmp != null) {
                    _avatarBitmap.value = bmp
                    Timber.tag(TAG).d("Loaded image for cropping: ${bmp.width}x${bmp.height}")
                } else {
                    Timber.tag(TAG).w("Failed to decode selected image")
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Error loading selected image")
            }
        }
    }

    /**
     * When user confirms crop — convert to circular avatar, save to cache,
     * replace any existing avatar (Mode A).
     */
    fun onCroppedSquareBitmapSaved(context: Context, croppedSquare: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val square = ImageUtil.centerCropSquare(croppedSquare)
                val circ = ImageUtil.toCircularBitmap(square)
                val path = ImageUtil.saveBitmapToCache(context, circ)
                if (path != null) {
                    // Replace existing avatar (Mode A)
                    _avatarPath.value = path
                    _avatarBitmap.value = circ

                    // Reset default transform for new avatar so it sits nicely
                    _avatarOffsetX.value = 12f
                    _avatarOffsetY.value = 12f
                    _avatarScale.value = 1f
                    _avatarRotation.value = 0f

                    Timber.tag(TAG).d("Avatar saved to cache → $path")
                } else {
                    Timber.tag(TAG).e("Failed to save avatar to cache")
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Error processing cropped bitmap")
            }
        }
    }

    /**
     * Replace avatar with null (clear). Mode A: keep this private or behind explicit UI.
     */
    fun clearAvatar() {
        _avatarPath.value = null
        _avatarBitmap.value = null
        // reset transforms
        _avatarOffsetX.value = 12f
        _avatarOffsetY.value = 12f
        _avatarScale.value = 1f
        _avatarRotation.value = 0f
    }

    // ---------------------------------------------------------
    // Avatar transform updates (called by AvatarDraggable composable)
    // ---------------------------------------------------------
    fun updateAvatarTransform(xDp: Float, yDp: Float, scale: Float, rotation: Float = 0f) {
        _avatarOffsetX.value = xDp
        _avatarOffsetY.value = yDp
        _avatarScale.value = scale
        _avatarRotation.value = rotation
    }

    // Optional individual updates
    fun updateAvatarOffset(xDp: Float, yDp: Float) {
        _avatarOffsetX.value = xDp
        _avatarOffsetY.value = yDp
    }

    fun updateAvatarScale(scale: Float) {
        _avatarScale.value = scale
    }

    // ---------------------------------------------------------
    // Existing reminder loading + card building code (unchanged)
    // ---------------------------------------------------------
    fun refresh() { if (reminderIdArg != -1L) loadReminder(reminderIdArg) }

    private fun loadReminder(id: Long) {
        viewModelScope.launch {
            _uiState.value = CardUiState.Loading
            try {
                val reminder = repo.getReminder(id)
                if (reminder == null) {
                    _uiState.value = CardUiState.Error("Reminder not found.")
                    return@launch
                }
                val cardData = buildCardData(reminder)
                _uiState.value = CardUiState.Data(cardData)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load reminder id=%d", id)
                _uiState.value = CardUiState.Error("Failed to load reminder.")
            }
        }
    }

    // =========================================================
    // Build CardData from EventReminder
    // =========================================================
    private fun buildCardData(reminder: EventReminder): CardData {

        // --------------------------
        // Resolve ZoneId safely
        // --------------------------
        val zone = try {
            ZoneId.of(reminder.timeZone)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Invalid timezone '%s'", reminder.timeZone)
            ZoneId.systemDefault()
        }

        // --------------------------
        // Original date
        // --------------------------
        val originalInstant = Instant.ofEpochMilli(reminder.eventEpochMillis)
        val originalZdt = ZonedDateTime.ofInstant(originalInstant, zone)

        // --------------------------
        // Next occurrence
        // --------------------------
        val nextEpochMillis = try {
            nextCalculator.nextOccurrence(
                reminder.eventEpochMillis,
                reminder.timeZone,
                reminder.repeatRule
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "NextOccurrenceCalculator failed")
            null
        }

        val nextInstant = when {
            nextEpochMillis != null -> Instant.ofEpochMilli(nextEpochMillis)
            originalInstant.isAfter(Instant.now()) -> originalInstant
            else -> null
        }

        // --------------------------
        // Date labels
        // --------------------------
        val originalLabel = originalZdt.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        val nextLabel = nextInstant?.atZone(zone)
            ?.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
            ?: "N/A"

        // --------------------------
        // Determine event type
        // --------------------------
        val eventKind = mapTitleToEventKind(reminder.title)

        // --------------------------
        // Age / Years (if needed)
        // --------------------------
        val yearsLabel = when (eventKind) {
            EventKind.BIRTHDAY, EventKind.ANNIVERSARY ->
                computeYearsLabel(originalZdt.toLocalDate(), nextInstant, zone)
            else -> null
        }

        // --------------------------
        // Final CardData
        // --------------------------
        return CardData(
            reminderId = reminder.id,
            title = reminder.title.ifBlank { "Event" },
            name = reminder.description,
            eventKind = eventKind,
            ageOrYearsLabel = yearsLabel,
            originalDateLabel = originalLabel,
            nextDateLabel = nextLabel,
            timezone = zone,
            stickers = emptyList()   // No default stickers (user adds manually)
        )
    }

    // =========================================================
    // Helpers
    // =========================================================
    private fun mapTitleToEventKind(title: String): EventKind {
        try {
            val match = ReminderTitle.entries.find { it.label.equals(title, true) }
            if (match != null) {
                return when (match) {
                    ReminderTitle.BIRTHDAY -> EventKind.BIRTHDAY
                    ReminderTitle.ANNIVERSARY -> EventKind.ANNIVERSARY
                    else -> EventKind.GENERIC
                }
            }
        } catch (_: Exception) {}

        val lower = title.lowercase()

        return when {
            "birth" in lower -> EventKind.BIRTHDAY
            "anniv" in lower -> EventKind.ANNIVERSARY
            else -> EventKind.GENERIC
        }
    }

    private fun computeYearsLabel(originalDate: LocalDate, nextInstant: Instant?, zone: ZoneId): String? {
        return try {
            val compareDate = (nextInstant ?: Instant.now()).atZone(zone).toLocalDate()

            var years = compareDate.year - originalDate.year
            if (compareDate.isBefore(originalDate.withYear(compareDate.year))) {
                years--
            }

            years.takeIf { it >= 0 }?.toString()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed computing years label")
            null
        }
    }
}
