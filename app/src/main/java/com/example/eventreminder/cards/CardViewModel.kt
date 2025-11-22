package com.example.eventreminder.cards

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.cards.model.*
import com.example.eventreminder.cards.state.CardUiState
import com.example.eventreminder.data.model.EventReminder
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
import java.io.File
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

    // UI state
    private val _uiState = MutableStateFlow<CardUiState>(CardUiState.Loading)
    val uiState: StateFlow<CardUiState> = _uiState.asStateFlow()

    // Avatar image state
    private val _avatarBitmap = MutableStateFlow<Bitmap?>(null)
    val avatarBitmap: StateFlow<Bitmap?> = _avatarBitmap.asStateFlow()

    private val _avatarPath = MutableStateFlow<String?>(null)
    val avatarPath: StateFlow<String?> = _avatarPath.asStateFlow()

    // Background persisted path string (cached file path or content URI string)
    private val _backgroundUriString = MutableStateFlow<String?>(null)
    val backgroundUriString: StateFlow<String?> = _backgroundUriString.asStateFlow()

    // Background decoded bitmap for UI preview (optional; VM updates when a new image is selected)
    private val _backgroundBitmap = MutableStateFlow<Bitmap?>(null)
    val backgroundBitmap: StateFlow<Bitmap?> = _backgroundBitmap.asStateFlow()

    // Avatar transform state (dp units)
    private val _avatarOffsetX = MutableStateFlow(12f)
    private val _avatarOffsetY = MutableStateFlow(12f)
    private val _avatarScale = MutableStateFlow(1f)
    private val _avatarRotation = MutableStateFlow(0f)

    val avatarOffsetX: StateFlow<Float> = _avatarOffsetX.asStateFlow()
    val avatarOffsetY: StateFlow<Float> = _avatarOffsetY.asStateFlow()
    val avatarScale: StateFlow<Float> = _avatarScale.asStateFlow()
    val avatarRotation: StateFlow<Float> = _avatarRotation.asStateFlow()

    // nav arg
    private val reminderIdArg: Long = savedStateHandle.get<Long>("reminderId") ?: -1L

    init {
        Timber.tag(TAG).d("init reminderId=%d", reminderIdArg)
        if (reminderIdArg == -1L) {
            _uiState.value = CardUiState.Placeholder
        } else {
            // load basic reminder data (without heavy bitmap decode here)
            refresh()
        }
    }

    fun refresh() {
        if (reminderIdArg != -1L) loadReminder(reminderIdArg)
    }

    private fun loadReminder(id: Long) {
        viewModelScope.launch {
            _uiState.value = CardUiState.Loading
            try {
                val reminder = repo.getReminder(id)
                if (reminder == null) {
                    _uiState.value = CardUiState.Error("Reminder not found.")
                    return@launch
                }

                // Persist background URI string (no decoding here; UI can request decoding)
                _backgroundUriString.value = reminder.backgroundUri

                val cardData = buildCardData(reminder)
                _uiState.value = CardUiState.Data(cardData)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load reminder id=%d", id)
                _uiState.value = CardUiState.Error("Failed to load reminder.")
            }
        }
    }

    /**
     * Helper: load bitmap from a saved path string (content://, file:// or plain file path)
     * Returns null on any failure.
     */
    private fun loadBitmapFromPathString(context: Context, pathStr: String): Bitmap? {
        return try {
            val uri = when {
                pathStr.startsWith("content://") || pathStr.startsWith("file://") -> Uri.parse(pathStr)
                else -> Uri.fromFile(File(pathStr))
            }
            ImageUtil.loadBitmapFromUri(context, uri, maxDim = 2000)
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "loadBitmapFromPathString failed for: $pathStr")
            null
        }
    } //p


    // Public helper for UI to ask VM to decode an existing persisted background path
    // This avoids requiring Application inside the VM init.
    fun loadExistingBackground(context: Context) {
        val uriStr = _backgroundUriString.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bmp = ImageUtil.loadBitmapFromPathString(context, uriStr, maxDim = 2000)
                _backgroundBitmap.value = bmp
            } catch (t: Throwable) {
                Timber.tag(TAG).w(t, "Failed loading existing background: $uriStr")
                _backgroundBitmap.value = null
            }
        }
    }

    /**
     * Called when the user picks a background image (Uri returned by picker).
     * Saves to cache, persists to DB and updates background bitmap in VM.
     */
    fun onBackgroundImageSelected(context: Context, imageUri: Uri) {
        if (reminderIdArg == -1L) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.tag(TAG).d("Loading background image from uri: $imageUri")
                val bmp = ImageUtil.loadBitmapFromUri(context, imageUri, maxDim = 2000)
                if (bmp == null) {
                    Timber.tag(TAG).w("Failed to decode background image")
                    return@launch
                }

                val cachedPath = ImageUtil.saveBitmapToCache(context, bmp, filenamePrefix = "bg_")
                if (cachedPath == null) {
                    Timber.tag(TAG).e("Failed to save background to cache")
                    return@launch
                }

                // update in-memory and persist
                _backgroundBitmap.value = bmp
                _backgroundUriString.value = cachedPath

                val reminder = repo.getReminder(reminderIdArg)
                if (reminder != null) {
                    val updated = reminder.copy(backgroundUri = cachedPath)
                    repo.update(updated)
                    Timber.tag(TAG).d("Persisted backgroundUri to reminder id=${reminder.id} path=$cachedPath")
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Error in onBackgroundImageSelected")
            }
        }
    }

    fun clearBackground() {
        if (reminderIdArg == -1L) {
            _backgroundBitmap.value = null
            _backgroundUriString.value = null
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _backgroundBitmap.value = null
                _backgroundUriString.value = null
                val reminder = repo.getReminder(reminderIdArg)
                if (reminder != null) {
                    val updated = reminder.copy(backgroundUri = null)
                    repo.update(updated)
                    Timber.tag(TAG).d("Cleared backgroundUri for reminder id=${reminder.id}")
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Failed clearing background")
            }
        }
    }

    // Avatar pipeline (same behavior as before)
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

    fun onCroppedSquareBitmapSaved(context: Context, croppedSquare: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val square = ImageUtil.centerCropSquare(croppedSquare)
                val circ = ImageUtil.toCircularBitmap(square)
                val path = ImageUtil.saveBitmapToCache(context, circ)
                if (path != null) {
                    _avatarPath.value = path
                    _avatarBitmap.value = circ

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

    fun clearAvatar() {
        _avatarPath.value = null
        _avatarBitmap.value = null
        _avatarOffsetX.value = 12f
        _avatarOffsetY.value = 12f
        _avatarScale.value = 1f
        _avatarRotation.value = 0f
    }

    fun updateAvatarTransform(xDp: Float, yDp: Float, scale: Float, rotation: Float = 0f) {
        _avatarOffsetX.value = xDp
        _avatarOffsetY.value = yDp
        _avatarScale.value = scale
        _avatarRotation.value = rotation
    }

    fun updateAvatarOffset(xDp: Float, yDp: Float) {
        _avatarOffsetX.value = xDp
        _avatarOffsetY.value = yDp
    } //p

    fun updateAvatarScale(scale: Float) {
        _avatarScale.value = scale
    } //p

    // Build CardData (unchanged)
    private fun buildCardData(reminder: EventReminder): CardData {
        val zone = try { ZoneId.of(reminder.timeZone) } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Invalid timezone '%s'", reminder.timeZone)
            ZoneId.systemDefault()
        }

        val originalInstant = Instant.ofEpochMilli(reminder.eventEpochMillis)
        val originalZdt = ZonedDateTime.ofInstant(originalInstant, zone)

        val nextEpochMillis = try {
            nextCalculator.nextOccurrence(reminder.eventEpochMillis, reminder.timeZone, reminder.repeatRule)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "NextOccurrenceCalculator failed")
            null
        }

        val nextInstant = when {
            nextEpochMillis != null -> Instant.ofEpochMilli(nextEpochMillis)
            originalInstant.isAfter(Instant.now()) -> originalInstant
            else -> null
        }

        val originalLabel = originalZdt.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        val nextLabel = nextInstant?.atZone(zone)?.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")) ?: "N/A"

        val eventKind = mapTitleToEventKind(reminder.title)

        val yearsLabel = when (eventKind) {
            EventKind.BIRTHDAY, EventKind.ANNIVERSARY ->
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

    private fun mapTitleToEventKind(title: String): EventKind {
        try {
            val match = com.example.eventreminder.data.model.ReminderTitle.entries.find { it.label.equals(title, true) }
            if (match != null) {
                return when (match) {
                    com.example.eventreminder.data.model.ReminderTitle.BIRTHDAY -> EventKind.BIRTHDAY
                    com.example.eventreminder.data.model.ReminderTitle.ANNIVERSARY -> EventKind.ANNIVERSARY
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

    private fun computeYearsLabel(originalDate: java.time.LocalDate, nextInstant: Instant?, zone: ZoneId): String? {
        return try {
            val compareDate = (nextInstant ?: Instant.now()).atZone(zone).toLocalDate()
            var years = compareDate.year - originalDate.year
            if (compareDate.isBefore(originalDate.withYear(compareDate.year))) years--
            years.takeIf { it >= 0 }?.toString()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed computing years label")
            null
        }
    }

    // =========================================================
    // Sticker APIs (unchanged)
    // =========================================================
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
    }  //p

    fun removeSticker(sticker: CardSticker) {
        val current = (_uiState.value as? CardUiState.Data)?.cardData ?: return

        Timber.tag(TAG).d("Removing sticker id=%S", sticker.id)

        _uiState.value = CardUiState.Data(
            current.copy(stickers = current.stickers.filter { it.id != sticker.id })
        )
    } //p

    fun updateSticker(sticker: CardSticker) {
        val current = (_uiState.value as? CardUiState.Data)?.cardData ?: return

        _uiState.value = CardUiState.Data(
            current.copy(
                stickers = current.stickers.map { if (it.id == sticker.id) sticker else it }
            )
        )
    } //p

}




/*
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
import com.example.eventreminder.cards.model.EventKind
import com.example.eventreminder.cards.model.StickerItem
import com.example.eventreminder.cards.state.CardUiState
import com.example.eventreminder.data.model.EventReminder
import com.example.eventreminder.data.model.ReminderTitle
import com.example.eventreminder.data.repo.ReminderRepository
import com.example.eventreminder.util.NextOccurrenceCalculator
import com.example.eventreminder.cards.util.ImageUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// =============================================================
// Constants
// =============================================================
private const val TAG = "CardViewModel"

// =============================================================
// CardViewModel
// - Hilt-injected ApplicationContext (option A chosen)
// - Handles avatar + background pipelines, stickers, and building CardData
// =============================================================
@HiltViewModel
class CardViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repo: ReminderRepository,
    private val nextCalculator: NextOccurrenceCalculator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // ---------------------------------------------------------
    // UI / Domain State
    // ---------------------------------------------------------
    private val _uiState = MutableStateFlow<CardUiState>(CardUiState.Loading)
    val uiState: StateFlow<CardUiState> = _uiState.asStateFlow()

    // Avatar state (in-memory bitmap + persisted cache path)
    private val _avatarBitmap = MutableStateFlow<Bitmap?>(null)
    val avatarBitmap: StateFlow<Bitmap?> = _avatarBitmap.asStateFlow()

    private val _avatarPath = MutableStateFlow<String?>(null)
    val avatarPath: StateFlow<String?> = _avatarPath.asStateFlow()

    // Background state (persisted to EventReminder.backgroundUri)
    private val _background = MutableStateFlow<CardBackground?>(null)
    val background: StateFlow<CardBackground?> = _background

    private val _backgroundBitmap = MutableStateFlow<Bitmap?>(null)
    val backgroundBitmap: StateFlow<Bitmap?> = _backgroundBitmap.asStateFlow()

    // ---------------------------------------------------------
    // Avatar transform (dp/float values) preserved in VM during editing
    // ---------------------------------------------------------
    private val _avatarOffsetX = MutableStateFlow(220f)
    private val _avatarOffsetY = MutableStateFlow(24f)
    private val _avatarScale = MutableStateFlow(1.1f)
    private val _avatarRotation = MutableStateFlow(0f)

    val avatarOffsetX: StateFlow<Float> = _avatarOffsetX.asStateFlow()
    val avatarOffsetY: StateFlow<Float> = _avatarOffsetY.asStateFlow()
    val avatarScale: StateFlow<Float> = _avatarScale.asStateFlow()
    val avatarRotation: StateFlow<Float> = _avatarRotation.asStateFlow()

    // Navigation argument (typed)
    private val reminderIdArg: Long = savedStateHandle.get<Long>("reminderId") ?: -1L

    init {
        Timber.tag(TAG).d("CardViewModel init → reminderId=%d", reminderIdArg)
        if (reminderIdArg == -1L) {
            _uiState.value = CardUiState.Placeholder
        } else {
            loadReminder(reminderIdArg)
        }
    }

    // =========================================================
    // Background helpers (persisted to Reminder.backgroundUri)
    // =========================================================

    */
/**
     * Called when the user picks a background image (URI returned by picker).
     * Steps:
     *  - decode with ImageUtil.loadBitmapFromUri()
     *  - save to cache via ImageUtil.saveBitmapToCache()
     *  - update in-memory bitmap for immediate UI
     *  - persist cached path to EventReminder.backgroundUri using repo.update(...)
     *//*

    fun onBackgroundImageSelected(context: Context, imageUri: Uri) {
        if (reminderIdArg == -1L) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.tag(TAG).d("Loading background image from uri: $imageUri")
                val bmp = ImageUtil.loadBitmapFromUri(context, imageUri, maxDim = 2000)
                if (bmp == null) {
                    Timber.tag(TAG).w("Failed to decode background image")
                    return@launch
                }

                // Save to app cache and persist the path string
                val cachedPath = ImageUtil.saveBitmapToCache(context, bmp, filenamePrefix = "bg_")
                if (cachedPath == null) {
                    Timber.tag(TAG).e("Failed to save background to cache")
                    return@launch
                }

                // Update in-memory bitmap for immediate UI
                _backgroundBitmap.value = bmp

                // Persist to DB: load reminder, update backgroundUri, call repo.update(reminder)
                val reminder = repo.getReminder(reminderIdArg)
                if (reminder != null) {
                    val updated = reminder.copy(backgroundUri = cachedPath)
                    repo.update(updated)
                    Timber.tag(TAG).d("Persisted backgroundUri to reminder id=${reminder.id} path=$cachedPath")
                } else {
                    Timber.tag(TAG).w("Reminder not found when saving background (id=$reminderIdArg)")
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Error in onBackgroundImageSelected")
            }
        }
    }

    */
/**
     * Clear the background image from memory + DB.
     *//*

    fun clearBackground() {
        if (reminderIdArg == -1L) {
            _backgroundBitmap.value = null
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _backgroundBitmap.value = null

                val reminder = repo.getReminder(reminderIdArg)
                if (reminder != null) {
                    val updated = reminder.copy(backgroundUri = null)
                    repo.update(updated)
                    Timber.tag(TAG).d("Cleared backgroundUri for reminder id=${reminder.id}")
                } else {
                    Timber.tag(TAG).w("Reminder not found when clearing background (id=$reminderIdArg)")
                }
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Failed clearing background")
            }
        }
    }

    // =========================================================
    // Sticker APIs (unchanged)
    // =========================================================
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
    }  //p

    fun removeSticker(sticker: CardSticker) {
        val current = (_uiState.value as? CardUiState.Data)?.cardData ?: return

        Timber.tag(TAG).d("Removing sticker id=%S", sticker.id)

        _uiState.value = CardUiState.Data(
            current.copy(stickers = current.stickers.filter { it.id != sticker.id })
        )
    } //p

    fun updateSticker(sticker: CardSticker) {
        val current = (_uiState.value as? CardUiState.Data)?.cardData ?: return

        _uiState.value = CardUiState.Data(
            current.copy(
                stickers = current.stickers.map { if (it.id == sticker.id) sticker else it }
            )
        )
    } //p

    // =========================================================
    // Avatar / Image pipeline
    // =========================================================

    */
/**
     * When user selects an image URI for avatar (before crop) — load downsampled preview.
     *//*

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

    */
/**
     * After cropping, convert to circular avatar, save to cache and update VM state.
     *//*

    fun onCroppedSquareBitmapSaved(context: Context, croppedSquare: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val square = ImageUtil.centerCropSquare(croppedSquare)
                val circ = ImageUtil.toCircularBitmap(square)
                val path = ImageUtil.saveBitmapToCache(context, circ)
                if (path != null) {
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

    fun clearAvatar() {
        _avatarPath.value = null
        _avatarBitmap.value = null
        _avatarOffsetX.value = 12f
        _avatarOffsetY.value = 12f
        _avatarScale.value = 1f
        _avatarRotation.value = 0f
    }

    fun updateAvatarTransform(xDp: Float, yDp: Float, scale: Float, rotation: Float = 0f) {
        _avatarOffsetX.value = xDp
        _avatarOffsetY.value = yDp
        _avatarScale.value = scale
        _avatarRotation.value = rotation
    }

    fun updateAvatarOffset(xDp: Float, yDp: Float) {
        _avatarOffsetX.value = xDp
        _avatarOffsetY.value = yDp
    } //p

    fun updateAvatarScale(scale: Float) {
        _avatarScale.value = scale
    } //p

    // =========================================================
    // Reminder loading — loads background bitmap (if saved) and CardData
    // =========================================================
    fun refresh() {
        if (reminderIdArg != -1L) loadReminder(reminderIdArg)
    }

    private fun loadReminder(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = CardUiState.Loading

            try {
                val reminder = repo.getReminder(id)
                if (reminder == null) {
                    _uiState.value = CardUiState.Error("Reminder not found.")
                    return@launch
                }

                // Load background from DB (cached path or content://)
                val bgUriString = reminder.backgroundUri
                if (!bgUriString.isNullOrBlank()) {
                    try {
                        val bmp = loadBitmapFromPathString(appContext, bgUriString)
                        _backgroundBitmap.value = bmp
                        Timber.tag(TAG).d("Loaded background for reminder id=$id from $bgUriString")
                    } catch (t: Throwable) {
                        Timber.tag(TAG).w(t, "Failed to load background from path: $bgUriString")
                        _backgroundBitmap.value = null
                    }
                } else {
                    _backgroundBitmap.value = null
                }

                val cardData = buildCardData(reminder)
                _uiState.value = CardUiState.Data(cardData)

            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to load reminder id=%d", id)
                _uiState.value = CardUiState.Error("Failed to load reminder.")
            }
        }
    }

    */
    /**
     * Helper: load bitmap from a saved path string (content://, file:// or plain file path)
     * Returns null on any failure.
     *//*

    private fun loadBitmapFromPathString(context: Context, pathStr: String): Bitmap? {
        return try {
            val uri = when {
                pathStr.startsWith("content://") || pathStr.startsWith("file://") -> Uri.parse(pathStr)
                else -> Uri.fromFile(File(pathStr))
            }
            ImageUtil.loadBitmapFromUri(context, uri, maxDim = 2000)
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "loadBitmapFromPathString failed for: $pathStr")
            null
        }
    } //p

    // =========================================================
    // Build CardData from EventReminder
    // =========================================================
    private fun buildCardData(reminder: EventReminder): CardData {

        // Resolve ZoneId safely
        val zone = try {
            ZoneId.of(reminder.timeZone)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Invalid timezone '%s'", reminder.timeZone)
            ZoneId.systemDefault()
        }

        // Original date
        val originalInstant = Instant.ofEpochMilli(reminder.eventEpochMillis)
        val originalZdt = ZonedDateTime.ofInstant(originalInstant, zone)

        // Next occurrence
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

        // Date labels
        val originalLabel = originalZdt.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
        val nextLabel = nextInstant?.atZone(zone)
            ?.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
            ?: "N/A"

        // Event kind
        val eventKind = mapTitleToEventKind(reminder.title)

        // Years / Age label
        val yearsLabel = when (eventKind) {
            EventKind.BIRTHDAY, EventKind.ANNIVERSARY ->
                computeYearsLabel(originalZdt.toLocalDate(), nextInstant, zone)
            else -> null
        }

        // Final CardData
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

    private fun computeYearsLabel(originalDate: java.time.LocalDate, nextInstant: Instant?, zone: ZoneId): String? {
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
*/
