package com.example.eventreminder.cards

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
import dagger.hilt.android.lifecycle.HiltViewModel
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

    // Typed navigation argument (from CardScreen ← NavGraph)
    private val reminderIdArg: Long = savedStateHandle.get<Long>("reminderId") ?: -1L

    init {
        Timber.tag(TAG)
            .d("CardViewModel init → reminderId=%d", reminderIdArg)

        if (reminderIdArg == -1L) {
            _uiState.value = CardUiState.Placeholder
        } else {
            loadReminder(reminderIdArg)
        }
    }

    // ---------------------------------------------------------
    // Public API
    // ---------------------------------------------------------
    fun refresh() {
        if (reminderIdArg != -1L) loadReminder(reminderIdArg)
    }

    // =========================================================
    // Load Reminder + Transform to CardData
    // =========================================================
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
    // Sticker Add / Update / Delete
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

    private fun computeYearsLabel(
        originalDate: LocalDate,
        nextInstant: Instant?,
        zone: ZoneId
    ): String? {
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
