package com.example.eventreminder.cards

// =============================================================
// Imports
// =============================================================
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.cards.model.CardBackground
import com.example.eventreminder.cards.model.CardData
import com.example.eventreminder.cards.pixelcanvas.stickers.model.StickerCatalogItem
import com.example.eventreminder.cards.pixelcanvas.stickers.model.StickerPx
import com.example.eventreminder.cards.util.ImageUtil
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.model.ReminderTitle
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.util.NextOccurrenceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private const val TAG = "CardViewModel"

@HiltViewModel
class CardViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val nextCalculator: NextOccurrenceCalculator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // =============================================================
    // UI STATE (Card metadata)
    // =============================================================
    private val _uiState = MutableStateFlow<CardUiState>(CardUiState.Loading)
    val uiState: StateFlow<CardUiState> = _uiState.asStateFlow()

    private val _background = MutableStateFlow<CardBackground?>(null)
    val background: StateFlow<CardBackground?> = _background.asStateFlow()

    private val _backgroundBitmap = MutableStateFlow<Bitmap?>(null)

    // =============================================================
    // UUID ARG — SINGLE SOURCE OF TRUTH
    // =============================================================
    private val reminderIdArg: String? = savedStateHandle.get<String>("reminderId")

    init {
        Timber.tag(TAG).d("init reminderId=%s", reminderIdArg)
        if (reminderIdArg.isNullOrBlank()) {
            _uiState.value = CardUiState.Placeholder
        } else {
            loadReminder(reminderIdArg)
        }
    }

    fun forceLoadReminder(id: String) {
        loadReminder(id)
    }

    // =============================================================
    // LOAD REMINDER (UUID)
    // =============================================================
    private fun loadReminder(id: String) {
        viewModelScope.launch {
            _uiState.value = CardUiState.Loading
            try {
                val reminder = repo.getReminder(id)
                if (reminder == null) {
                    _uiState.value = CardUiState.Error("Reminder not found.")
                    return@launch
                }

                // Load background bitmap (if exists)
                reminder.backgroundUri?.let { uriStr ->
                    try {
                        val bmp = ImageUtil.loadBitmapFromPathString(uriStr)
                        _backgroundBitmap.value = bmp
                    } catch (t: Throwable) {
                        Timber.tag(TAG).w(t, "Failed to load background")
                        _backgroundBitmap.value = null
                    }
                }

                val cardData = buildCardData(reminder)
                _uiState.value = CardUiState.Data(cardData)

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "loadReminder failed")
                _uiState.value = CardUiState.Error("Failed to load reminder.")
            }
        }
    }

    // =============================================================
    // BUILD CARD DATA (UUID OK)
    // =============================================================
    private fun buildCardData(reminder: EventReminder): CardData {
        val zone = try { ZoneId.of(reminder.timeZone) }
        catch (_: Exception) { ZoneId.systemDefault() }

        val originalInstant = Instant.ofEpochMilli(reminder.eventEpochMillis)
        val originalZdt = ZonedDateTime.ofInstant(originalInstant, zone)

        val nextEpoch = try {
            nextCalculator.nextOccurrence(reminder.eventEpochMillis, reminder.timeZone, reminder.repeatRule)
        } catch (_: Exception) { null }

        val nextInstant = nextEpoch?.let { Instant.ofEpochMilli(it) }
            ?: originalInstant.takeIf { it.isAfter(Instant.now()) }

        val originalLabel = originalZdt.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        val nextLabel = nextInstant?.atZone(zone)
            ?.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")) ?: "N/A"

        val eventKind = mapTitleToEventKind(reminder.title)

        val yearsLabel =
            if (eventKind == com.example.eventreminder.cards.model.EventKind.BIRTHDAY ||
                eventKind == com.example.eventreminder.cards.model.EventKind.ANNIVERSARY
            ) computeYearsLabel(originalZdt.toLocalDate(), nextInstant, zone)
            else null

        return CardData(
            reminderId = reminder.id,   // UUID
            title = reminder.title.ifBlank { "Event" },
            name = reminder.description,
            eventKind = eventKind,
            ageOrYearsLabel = yearsLabel,
            originalDateLabel = originalLabel,
            nextDateLabel = nextLabel,
            timezone = zone,
            stickers = emptyList()
        )
    }

    private fun mapTitleToEventKind(title: String) = try {
        when (ReminderTitle.entries.find { it.label.equals(title, true) }) {
            ReminderTitle.BIRTHDAY -> com.example.eventreminder.cards.model.EventKind.BIRTHDAY
            ReminderTitle.ANNIVERSARY -> com.example.eventreminder.cards.model.EventKind.ANNIVERSARY
            else -> com.example.eventreminder.cards.model.EventKind.GENERIC
        }
    } catch (_: Exception) {
        val lower = title.lowercase()
        when {
            "birth" in lower -> com.example.eventreminder.cards.model.EventKind.BIRTHDAY
            "anniv" in lower -> com.example.eventreminder.cards.model.EventKind.ANNIVERSARY
            else -> com.example.eventreminder.cards.model.EventKind.GENERIC
        }
    }

    private fun computeYearsLabel(originalDate: java.time.LocalDate, nextInstant: Instant?, zone: ZoneId): String? {
        return try {
            val now = Instant.now().atZone(zone).toLocalDate()
            var years = now.year - originalDate.year
            if (now.isBefore(originalDate.withYear(now.year))) years--
            years.takeIf { it >= 0 }?.toString()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "computeYearsLabel failed")
            null
        }
    }

    // =============================================================
    // PIXEL AVATAR SYSTEM — FULL MERGE
    // =============================================================
    private val _pixelAvatarBitmap = MutableStateFlow<Bitmap?>(null)
    val pixelAvatarBitmap: StateFlow<Bitmap?> = _pixelAvatarBitmap.asStateFlow()

    private val _pixelAvatarXNorm = MutableStateFlow(0.5f)
    private val _pixelAvatarYNorm = MutableStateFlow(0.5f)
    private val _pixelAvatarScale = MutableStateFlow(1.35f)
    private val _pixelAvatarRotationDeg = MutableStateFlow(0f)

    val pixelAvatarXNorm = _pixelAvatarXNorm.asStateFlow()
    val pixelAvatarYNorm = _pixelAvatarYNorm.asStateFlow()
    val pixelAvatarScale = _pixelAvatarScale.asStateFlow()
    val pixelAvatarRotationDeg = _pixelAvatarRotationDeg.asStateFlow()

    fun onPixelAvatarImageSelected(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bmp = ImageUtil.loadBitmapFromUri(context, uri, maxDim = 720)
                if (bmp != null) {
                    val square = ImageUtil.centerCropSquare(bmp)
                    _pixelAvatarBitmap.value = square
                    _pixelAvatarXNorm.value = 0.5f
                    _pixelAvatarYNorm.value = 0.5f
                    _pixelAvatarScale.value = 1.35f
                    _pixelAvatarRotationDeg.value = 0f
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Avatar load failed")
            }
        }
    }

    fun clearPixelAvatar() {
        _pixelAvatarBitmap.value = null
        _pixelAvatarXNorm.value = 0.5f
        _pixelAvatarYNorm.value = 0.5f
        _pixelAvatarScale.value = 1f
        _pixelAvatarRotationDeg.value = 0f
    }

    fun updatePixelAvatarPosition(dx: Float, dy: Float) {
        _pixelAvatarXNorm.value = (_pixelAvatarXNorm.value + dx).coerceIn(-0.5f, 1.5f)
        _pixelAvatarYNorm.value = (_pixelAvatarYNorm.value + dy).coerceIn(-0.5f, 1.5f)
    }

    fun updatePixelAvatarScale(scale: Float) {
        _pixelAvatarScale.value = (_pixelAvatarScale.value * scale).coerceIn(0.1f, 8f)
    }

    fun updatePixelAvatarRotation(delta: Float) {
        _pixelAvatarRotationDeg.value =
            (_pixelAvatarRotationDeg.value + delta).mod(360f)
    }

    // =============================================================
    // PIXEL STICKER SYSTEM — FULL MERGE
    // =============================================================
    private val _pixelStickers = MutableStateFlow<List<StickerPx>>(emptyList())
    val pixelStickers = _pixelStickers.asStateFlow()

    private val _activeStickerId = MutableStateFlow<Long?>(null)
    val activeStickerId = _activeStickerId.asStateFlow()

    fun addStickerFromCatalog(item: StickerCatalogItem) {
        val newSticker = StickerPx(
            id = System.currentTimeMillis(),
            drawableResId = item.resId,
            bitmap = null,
            text = item.text,
            xNorm = 0.12f,
            yNorm = 0.82f,
            scale = 1f,
            rotationDeg = 0f
        )
        _pixelStickers.value = _pixelStickers.value + newSticker
        _activeStickerId.value = newSticker.id
    }

    fun removeSticker(stickerId: Long) {
        _pixelStickers.value = _pixelStickers.value.filterNot { it.id == stickerId }
        if (_activeStickerId.value == stickerId) _activeStickerId.value = null
    }

    fun setActiveSticker(id: Long?) {
        _activeStickerId.value = id
    }

    fun updateActiveStickerPosition(dx: Float, dy: Float) {
        val id = _activeStickerId.value ?: return
        _pixelStickers.value = _pixelStickers.value.map {
            if (it.id != id) it else it.copy(
                xNorm = (it.xNorm + dx).coerceIn(-5f, 6f),
                yNorm = (it.yNorm + dy).coerceIn(-5f, 6f)
            )
        }
    }

    fun updateActiveStickerScale(scale: Float) {
        val id = _activeStickerId.value ?: return
        _pixelStickers.value = _pixelStickers.value.map {
            if (it.id != id) it else it.copy(
                scale = (it.scale * scale).coerceIn(0.15f, 6f)
            )
        }
    }

    fun updateActiveStickerRotation(delta: Float) {
        val id = _activeStickerId.value ?: return
        _pixelStickers.value = _pixelStickers.value.map {
            if (it.id != id) it else it.copy(
                rotationDeg = (it.rotationDeg + delta).mod(360f)
            )
        }
    }
}
