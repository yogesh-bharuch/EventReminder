package com.example.eventreminder.cards

// =============================================================
// Imports
// =============================================================
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventreminder.R
import com.example.eventreminder.cards.model.CardData
import com.example.eventreminder.cards.model.CardSticker
import com.example.eventreminder.cards.model.EventKind
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

    // Pulled from navigation arguments (typed navigation)
    private val reminderIdArg: Long = savedStateHandle.get<Long>("reminderId") ?: -1L

    init {
        Timber.tag(TAG).d("Initialized CardViewModel with reminderId=%d", reminderIdArg)

        if (reminderIdArg == -1L) {
            _uiState.value = CardUiState.Placeholder
        } else {
            loadReminder(reminderIdArg)
        }
    }

    fun refresh() {
        if (reminderIdArg != -1L) {
            loadReminder(reminderIdArg)
        }
    }

    // =========================================================
    // Load Reminder
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

                val card = buildCardData(reminder)

                _uiState.value = CardUiState.Data(card)

            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Failed to load reminder id=%d", id)
                _uiState.value = CardUiState.Error("Failed to load reminder.")
            }
        }
    }

    // =========================================================
    // Build CardData (Core transformation)
    // =========================================================
    private fun buildCardData(reminder: EventReminder): CardData {

        // --------------------------
        // Safety: ZoneId
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
        } catch (ex: Exception) {
            Timber.tag(TAG).w(ex, "NextOccurrenceCalculator failed")
            null
        }

        val nextInstantResolved = when {
            nextEpochMillis != null -> Instant.ofEpochMilli(nextEpochMillis)
            originalInstant.isAfter(Instant.now()) -> originalInstant
            else -> null
        }

        // --------------------------
        // Formatting labels
        // --------------------------
        val fmtOriginal = DateTimeFormatter.ofPattern("MMM d, yyyy")
        val fmtNext = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")

        val originalLabel = originalZdt.format(fmtOriginal)
        val nextLabel = nextInstantResolved?.atZone(zone)?.format(fmtNext) ?: "N/A"

        // --------------------------
        // Determine kind (birthday / anniversary / generic)
        // --------------------------
        val eventKind = mapTitleToEventKind(reminder.title)

        // --------------------------
        // Age / Years label (only for Birthday/Anniversary)
        // --------------------------
        val yearsLabel = when (eventKind) {
            EventKind.BIRTHDAY, EventKind.ANNIVERSARY ->
                computeYearsLabel(originalZdt.toLocalDate(), nextInstantResolved, zone)
            else -> null
        }

        // --------------------------
        // Base CardData
        // --------------------------
        val baseCard = CardData(
            reminderId = reminder.id,
            title = reminder.title.ifBlank { "Event" },
            name = reminder.description,
            eventKind = eventKind,
            ageOrYearsLabel = yearsLabel,
            originalDateLabel = originalLabel,
            nextDateLabel = nextLabel,
            timezone = zone
        )

        // =====================================================
        // ⭐ STICKER INJECTION (TODO-8 Phase-2)
        // =====================================================
        val stickers = listOf(
            CardSticker(
                drawableResId = R.drawable.ic_image2, // ← your custom sticker file
                x = 144f,
                y = 48f,
                scale = 1.1f
            ),
            CardSticker(
                drawableResId = R.drawable.ic_cake, // ← your custom sticker file
                x = 144f,
                y = 144f,
                scale = 0.8f
            )
        )

        return baseCard.copy(stickers = stickers)
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
            val compareDate = (nextInstant ?: Instant.now())
                .atZone(zone).toLocalDate()

            var years = compareDate.year - originalDate.year
            if (compareDate.isBefore(originalDate.withYear(compareDate.year))) {
                years--
            }

            if (years >= 0) years.toString() else null

        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed computing years label")
            null
        }
    }
}
