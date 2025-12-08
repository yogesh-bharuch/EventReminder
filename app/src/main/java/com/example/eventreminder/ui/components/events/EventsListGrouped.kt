package com.example.eventreminder.ui.components.events

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eventreminder.ui.components.events.section.GroupedSectionContent
import com.example.eventreminder.ui.components.events.section.GroupedSectionHeader
import com.example.eventreminder.ui.viewmodels.GroupedUiSection
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import timber.log.Timber

private const val TAG = "EventsListGrouped"

// =============================================================
// LazyListState Saver (scroll position persistence)
// =============================================================
private val LazyListStateSaver = mapSaver(
    save = { state ->
        mapOf(
            "index" to state.firstVisibleItemIndex,
            "offset" to state.firstVisibleItemScrollOffset
        )
    },
    restore = { map ->
        LazyListState(
            firstVisibleItemIndex = map["index"] as Int,
            firstVisibleItemScrollOffset = map["offset"] as Int
        )
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsListGrouped(
    sections: List<GroupedUiSection>,
    viewModel: ReminderViewModel,
    onClick: (String) -> Unit,     // <-- UUID FIXED HERE
    modifier: Modifier = Modifier
) {
    Timber.tag(TAG).d("Rendering EventsListGrouped")

    val snackbarHostState = remember { SnackbarHostState() }

    // ------------------------------------------------------------
    // 1) REMEMBER COLLAPSED STATES ACROSS NAVIGATION
    // ------------------------------------------------------------
    var collapsed by rememberSaveable {
        mutableStateOf<Map<String, Boolean>>(emptyMap())
    }

    // Initialize only new sections
    sections.forEach { section ->
        if (!collapsed.containsKey(section.header)) {
            collapsed = collapsed + (section.header to true)  // default collapsed
        }
    }

    // ------------------------------------------------------------
    // 2) REMEMBER SCROLL POSITION ACROSS NAVIGATION
    // ------------------------------------------------------------
    val listState: LazyListState = rememberSaveable(
        saver = LazyListStateSaver
    ) {
        LazyListState()
    }

    Box(modifier.fillMaxSize()) {

        LazyColumn(state = listState) {

            sections.forEach { section ->

                // =============================================================
                // STICKY HEADER (remains pinned while scrolling)
                // =============================================================
                stickyHeader {
                    Surface(tonalElevation = 2.dp) {
                        GroupedSectionHeader(
                            header = section.header,
                            isCollapsed = collapsed[section.header] ?: true,
                            onToggle = {
                                val isNow = collapsed[section.header] ?: true
                                collapsed = collapsed + (section.header to !isNow)
                            }
                        )
                    }
                }

                // =============================================================
                // SECTION CONTENT
                // =============================================================
                item {
                    GroupedSectionContent(
                        events = section.events,
                        collapsed = collapsed[section.header] ?: true,
                        viewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        onClick = onClick
                    )
                }
            }
        }

        // Snackbar Host
        SnackbarHost(
            snackbarHostState,
            modifier = Modifier.padding(bottom = 60.dp)
        )
    }
}
