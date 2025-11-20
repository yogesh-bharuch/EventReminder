package com.example.eventreminder.cards.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * CaptureController
 *
 * UI-layer event channel used to trigger bitmap capture of a composable.
 */
class CaptureController {

    private val _request = MutableStateFlow(false)
    val request: StateFlow<Boolean> = _request

    fun capture() {
        _request.value = true
    }

    fun consumed() {
        _request.value = false
    }
}
