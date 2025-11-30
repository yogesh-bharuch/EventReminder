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
import com.example.eventreminder.cards.model.CardSticker
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
import android.graphics.Paint
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import android.graphics.Canvas
import com.example.eventreminder.cards.pixel.AvatarTransformPx
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// =============================================================
// TAG
// =============================================================
private const val TAG = "CardViewModel"

// =============================================================
// CardViewModel (Hilt)
// - persists background and avatar to cache & reminder DB
// - keeps transform state for avatar
// =============================================================
@HiltViewModel
class CardViewModel @Inject constructor(
    private val repo: ReminderRepository,
    private val nextCalculator: NextOccurrenceCalculator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<CardUiState>(CardUiState.Loading)
    val uiState: StateFlow<CardUiState> = _uiState.asStateFlow()

    // avatar
    private val _avatarBitmap = MutableStateFlow<Bitmap?>(null)
    val avatarBitmap: StateFlow<Bitmap?> = _avatarBitmap.asStateFlow()

    private val _avatarPath = MutableStateFlow<String?>(null)
    val avatarPath: StateFlow<String?> = _avatarPath.asStateFlow()

    // background (persisted)
    private val _background = MutableStateFlow<CardBackground?>(null)
    val background: StateFlow<CardBackground?> = _background.asStateFlow()

    private val _backgroundBitmap = MutableStateFlow<Bitmap?>(null)
    val backgroundBitmap: StateFlow<Bitmap?> = _backgroundBitmap.asStateFlow()


    // avatar transform state (dp & floats)
    private val _avatarOffsetX = MutableStateFlow(220f)
    private val _avatarOffsetY = MutableStateFlow(24f)
    private val _avatarScale = MutableStateFlow(1.1f)
    private val _avatarRotation = MutableStateFlow(0f)

    val avatarOffsetX: StateFlow<Float> = _avatarOffsetX.asStateFlow()
    val avatarOffsetY: StateFlow<Float> = _avatarOffsetY.asStateFlow()
    val avatarScale: StateFlow<Float> = _avatarScale.asStateFlow()
    val avatarRotation: StateFlow<Float> = _avatarRotation.asStateFlow()

    private val reminderIdArg: Long = savedStateHandle.get<Long>("reminderId") ?: -1L



    init {
        Timber.tag(TAG).d("init reminderId=%d", reminderIdArg)
        if (reminderIdArg == -1L) {
            _uiState.value = CardUiState.Placeholder
        } else {
            loadReminder(reminderIdArg)
        }
    }

    // -------------------------
    // Show/Hide title in card preview
    // -------------------------
    val showTitle = MutableStateFlow(true)
    val showName = MutableStateFlow(true)

    fun toggleShowTitle(value: Boolean) {
        showTitle.value = value
    }

    fun toggleShowName(value: Boolean) {
        showName.value = value
    }


    // -------------------------
    // Background helpers
    // -------------------------
    fun onBackgroundImageSelected(context: Context, imageUri: Uri) {
        if (reminderIdArg == -1L) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bmp = ImageUtil.loadBitmapFromUri(context, imageUri, maxDim = 2000)
                if (bmp == null) {
                    Timber.tag(TAG).w("decode failed for background")
                    return@launch
                }
                val cachedPath = ImageUtil.saveBitmapToCache(context, bmp, filenamePrefix = "bg_")
                if (cachedPath == null) {
                    Timber.tag(TAG).e("save to cache failed")
                    return@launch
                }
                _backgroundBitmap.value = bmp

                val r = repo.getReminder(reminderIdArg)
                if (r != null) {
                    repo.update(r.copy(backgroundUri = cachedPath))
                    Timber.tag(TAG).d("persisted backgroundUri for id=%d", r.id)
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "onBackgroundImageSelected error")
            }
        }
    }

    fun clearBackground() {
        if (reminderIdArg == -1L) {
            _backgroundBitmap.value = null
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _backgroundBitmap.value = null
                val r = repo.getReminder(reminderIdArg)
                if (r != null) repo.update(r.copy(backgroundUri = null))
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "clearBackground failed")
            }
        }
    }

    // -------------------------
    // Sticker APIs
    // -------------------------
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
        _uiState.value = CardUiState.Data(current.copy(stickers = current.stickers + newSticker))
    }

    fun removeSticker(sticker: CardSticker) {
        val current = (_uiState.value as? CardUiState.Data)?.cardData ?: return
        _uiState.value = CardUiState.Data(current.copy(stickers = current.stickers.filter { it.id != sticker.id }))
    }

    fun updateSticker(sticker: CardSticker) {
        val current = (_uiState.value as? CardUiState.Data)?.cardData ?: return
        _uiState.value = CardUiState.Data(current.copy(stickers = current.stickers.map { if (it.id == sticker.id) sticker else it }))
    }

    // -------------------------
    // Avatar pipeline
    // -------------------------
    fun onImageUriSelected(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bmp = ImageUtil.loadBitmapFromUri(context, uri, maxDim = 1600)
                if (bmp != null) _avatarBitmap.value = bmp
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "onImageUriSelected")
            }
        }
    }

    fun onCroppedSquareBitmapSaved(context: Context, croppedSquare: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val square = ImageUtil.centerCropSquare(croppedSquare)
                val circ = ImageUtil.toCircularBitmap(square)
                val path = ImageUtil.saveBitmapToCache(context, circ)
                if (path != null) {
                    _avatarPath.value = path
                    _avatarBitmap.value = circ
                    // reset transforms
                    _avatarOffsetX.value = 12f
                    _avatarOffsetY.value = 12f
                    _avatarScale.value = 1f
                    _avatarRotation.value = 0f
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "onCroppedSquareBitmapSaved")
            }
        }
    }

    fun clearAvatar() {
        _avatarPath.value = null
        _avatarBitmap.value = null
        _avatarOffsetX.value = 12f
        _avatarOffsetY.value = 12f
        _avatarScale.value = 1f
        _avatarRotation.value = 0f
    }

    // transform setters (called by AvatarDraggable)
    fun updateAvatarTransform(xDp: Float, yDp: Float, scale: Float, rotation: Float = 0f) {
        _avatarOffsetX.value = xDp
        _avatarOffsetY.value = yDp
        _avatarScale.value = scale
        _avatarRotation.value = rotation
    }

    fun updateAvatarOffset(xDp: Float, yDp: Float) {
        _avatarOffsetX.value = xDp
        _avatarOffsetY.value = yDp
    }

    fun updateAvatarScale(scale: Float) {
        _avatarScale.value = scale
    }

    // -------------------------
    // Loading reminder + background
    // -------------------------
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

                // load background bitmap if persisted (backgroundUri may be file path)
                val bgUri = reminder.backgroundUri
                if (!bgUri.isNullOrBlank()) {
                    try {
                        val bmp = ImageUtil.loadBitmapFromPathString(bgUri)
                        _backgroundBitmap.value = bmp
                    } catch (t: Throwable) {
                        Timber.tag(TAG).w(t, "Failed to load background")
                        _backgroundBitmap.value = null
                    }
                } else {
                    _backgroundBitmap.value = null
                }

                val cardData = buildCardData(reminder)
                _uiState.value = CardUiState.Data(cardData)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "loadReminder failed")
                _uiState.value = CardUiState.Error("Failed to load reminder.")
            }
        }
    }

    // -------------------------
    // Build CardData
    // -------------------------
    private fun buildCardData(reminder: EventReminder): CardData {
        val zone = try { ZoneId.of(reminder.timeZone) } catch (_: Exception) { ZoneId.systemDefault() }
        val originalInstant = Instant.ofEpochMilli(reminder.eventEpochMillis)
        val originalZdt = ZonedDateTime.ofInstant(originalInstant, zone)
        val nextEpochMillis = try {
            nextCalculator.nextOccurrence(reminder.eventEpochMillis, reminder.timeZone, reminder.repeatRule)
        } catch (_: Exception) { null }
        val nextInstant = when {
            nextEpochMillis != null -> Instant.ofEpochMilli(nextEpochMillis)
            originalInstant.isAfter(Instant.now()) -> originalInstant
            else -> null
        }
        val originalLabel = originalZdt.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        val nextLabel = nextInstant?.atZone(zone)?.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")) ?: "N/A"
        val eventKind = mapTitleToEventKind(reminder.title)
        val yearsLabel = when (eventKind) {
            com.example.eventreminder.cards.model.EventKind.BIRTHDAY,
            com.example.eventreminder.cards.model.EventKind.ANNIVERSARY ->
                computeYearsLabel(originalZdt.toLocalDate(), nextInstant, zone)
            else -> null
        }

        return CardData(
            reminderId = reminder.id,
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
        val match = ReminderTitle.entries.find { it.label.equals(title, true) }
        when (match) {
            com.example.eventreminder.data.model.ReminderTitle.BIRTHDAY -> com.example.eventreminder.cards.model.EventKind.BIRTHDAY
            com.example.eventreminder.data.model.ReminderTitle.ANNIVERSARY -> com.example.eventreminder.cards.model.EventKind.ANNIVERSARY
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
            val compareDate = (nextInstant ?: Instant.now()).atZone(zone).toLocalDate()
            var years = compareDate.year - originalDate.year
            if (compareDate.isBefore(originalDate.withYear(compareDate.year))) years--
            years.takeIf { it >= 0 }?.toString()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "computeYearsLabel failed")
            null
        }
    }


    // =============================================================
    // PIXEL AVATAR SYSTEM (STEP-2) — FINAL UPDATED VERSION
    // - SAF / PhotoPicker based avatar loader
    // - Downscale to 720px short-side
    // - Square center-crop for PixelRenderer
    // - Medium-quality avatar default
    // - Normalized transform fields (0–1 + scale + rotation)
    // - Exposes transform as AvatarTransformPx (CardDataPx compatible)
    // - Does NOT touch old DP-based avatar pipeline
    // =============================================================

    // Pixel avatar bitmap (free-layer; like sticker)
    private val _pixelAvatarBitmap = MutableStateFlow<Bitmap?>(null)
    val pixelAvatarBitmap: StateFlow<Bitmap?> = _pixelAvatarBitmap.asStateFlow()

    // Normalized center position (0..1)
    private val _pixelAvatarXNorm = MutableStateFlow(0.5f)
    private val _pixelAvatarYNorm = MutableStateFlow(0.5f)
    val pixelAvatarXNorm: StateFlow<Float> = _pixelAvatarXNorm.asStateFlow()
    val pixelAvatarYNorm: StateFlow<Float> = _pixelAvatarYNorm.asStateFlow()

    // Scale (multiplier) and rotation (degrees)
    private val _pixelAvatarScale = MutableStateFlow(1f)
    private val _pixelAvatarRotationDeg = MutableStateFlow(0f)
    val pixelAvatarScale: StateFlow<Float> = _pixelAvatarScale.asStateFlow()
    val pixelAvatarRotationDeg: StateFlow<Float> = _pixelAvatarRotationDeg.asStateFlow()

    // Quality for renderer (optional)
    private val _pixelAvatarQuality = MutableStateFlow(1)
    val pixelAvatarQuality: StateFlow<Int> = _pixelAvatarQuality.asStateFlow()

    /**
     * Export AvatarTransformPx (compatible with CardDataPx.AvatarTransformPx)
     * This is the canonical transform the renderer will read.
     */
    val pixelAvatarTransform: AvatarTransformPx
        get() = AvatarTransformPx(
            xNorm = _pixelAvatarXNorm.value,
            yNorm = _pixelAvatarYNorm.value,
            scale = _pixelAvatarScale.value,
            rotationDeg = _pixelAvatarRotationDeg.value
        )


    /**
     * SAF / PhotoPicker entrypoint.
     * Downscale via ImageUtil (maxDim=720), center-crop square (preserve symmetry), emit bitmap.
     */
    fun onPixelAvatarImageSelected(context: Context, uri: Uri) {
        Timber.tag(TAG).d("onPixelAvatarImageSelected: %s", uri)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loaded = ImageUtil.loadBitmapFromUri(context, uri, maxDim = 720)
                if (loaded == null) {
                    Timber.tag(TAG).w("onPixelAvatarImageSelected: decode returned null")
                    return@launch
                }

                // Keep square for easier transforms (center crop)
                val square = ImageUtil.centerCropSquare(loaded)
                _pixelAvatarBitmap.value = square

                // Reset transforms to sensible defaults (center + 1x + 0deg)
                _pixelAvatarXNorm.value = 0.5f
                _pixelAvatarYNorm.value = 0.5f
                _pixelAvatarScale.value = 1f
                _pixelAvatarRotationDeg.value = 0f
                _pixelAvatarQuality.value = 1

                Timber.tag(TAG).d("Pixel avatar set -> %dx%d", square.width, square.height)
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "onPixelAvatarImageSelected failed")
            }
        }
    }

    /**
     * Set avatar bitmap directly (used by placeholder generator or restore)
     */
    fun setPixelAvatar(bitmap: Bitmap) {
        Timber.tag(TAG).d("setPixelAvatar() size=%dx%d", bitmap.width, bitmap.height)
        _pixelAvatarBitmap.value = bitmap

        // Reset to sensible defaults
        _pixelAvatarXNorm.value = 0.5f
        _pixelAvatarYNorm.value = 0.5f
        _pixelAvatarScale.value = 1f
        _pixelAvatarRotationDeg.value = 0f
    }

    /** Clear avatar and reset defaults */
    fun clearPixelAvatar() {
        Timber.tag(TAG).d("clearPixelAvatar()")
        _pixelAvatarBitmap.value = null
        _pixelAvatarXNorm.value = 0.5f
        _pixelAvatarYNorm.value = 0.5f
        _pixelAvatarScale.value = 1f
        _pixelAvatarRotationDeg.value = 0f
        _pixelAvatarQuality.value = 1
    }

    /**
     * Update normalized center position by delta normalized (0..1 = fraction of card width/height).
     * dxNorm/dyNorm are computed in the Compose gesture layer as (pan.x / boxSize.width) etc.
     */
    fun updatePixelAvatarPosition(dxNorm: Float, dyNorm: Float) {
        val newX = (_pixelAvatarXNorm.value + dxNorm).coerceIn(-5f, 6f) // constrain to a safe region:
        val newY = (_pixelAvatarYNorm.value + dyNorm).coerceIn(-5f, 6f)
        _pixelAvatarXNorm.value = newX
        _pixelAvatarYNorm.value = newY
        Timber.tag("VM_AVATAR").d("Move → x=%.3f y=%.3f", newX, newY)
    }

    /** Multiply scale by factor, clamped for safety */
    fun updatePixelAvatarScale(scaleFactor: Float) {
        val newScale = (_pixelAvatarScale.value * scaleFactor).coerceIn(0.1f, 8f)
        _pixelAvatarScale.value = newScale
        Timber.tag("VM_AVATAR").d("Scale → %.3f", newScale)
    }

    /** Add delta degrees to rotation */
    fun updatePixelAvatarRotation(deltaDeg: Float) {
        val newRot = (_pixelAvatarRotationDeg.value + deltaDeg).mod(360f)
        _pixelAvatarRotationDeg.value = newRot
        Timber.tag("VM_AVATAR").d("Rotate → %.1f°", newRot)
    }

    /** Optional helper to explicitly set transform (useful for resets or preset placement) */
    fun setPixelAvatarTransform(xNorm: Float, yNorm: Float, scale: Float, rotationDeg: Float) {
        _pixelAvatarXNorm.value = xNorm.coerceIn(0f, 1f)
        _pixelAvatarYNorm.value = yNorm.coerceIn(0f, 1f)
        _pixelAvatarScale.value = scale
        _pixelAvatarRotationDeg.value = rotationDeg.mod(360f)
    }
    // =============================================================
    // Placeholder Avatar Generator (Debug/Test)
    // =============================================================
    fun loadPixelAvatarPlaceholder() {
        Timber.tag(TAG).d("loadPixelAvatarPlaceholder()")

        try {
            val size = 600
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            // Fill with light gray
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.LTGRAY
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

            Timber.tag(TAG).d("Placeholder created: %dx%d", bmp.width, bmp.height)

            setPixelAvatar(bmp)

        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to generate placeholder avatar")
        }
    }

}

