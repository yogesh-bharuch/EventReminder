package com.example.eventreminder.ui.components.events

// =============================================================
// Imports
// =============================================================
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.eventreminder.ui.components.events.section.GroupedSectionContent
import com.example.eventreminder.ui.components.events.section.GroupedSectionHeader
import com.example.eventreminder.ui.viewmodels.GroupedUiSection
import com.example.eventreminder.ui.viewmodels.ReminderViewModel
import timber.log.Timber

private const val TAG = "EventsListGrouped"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsListGrouped(
    sections: List<GroupedUiSection>,
    viewModel: ReminderViewModel,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Timber.tag(TAG).d("Rendering EventsListGrouped")

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutine = rememberCoroutineScope()

    val collapsed = remember { mutableStateMapOf<String, Boolean>() }

    // Initialize collapsed states
    sections.forEach { section ->
        if (!collapsed.containsKey(section.header)) collapsed[section.header] = true
    }

    Box(modifier.fillMaxSize()) {

        LazyColumn {
            sections.forEach { section ->

                // SECTION HEADER
                item {
                    GroupedSectionHeader(
                        header = section.header,
                        isCollapsed = collapsed[section.header] ?: true,
                        onToggle = {
                            collapsed[section.header] = !(collapsed[section.header] ?: false)
                        }
                    )
                }

                // SECTION CONTENT (collapsible)
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
