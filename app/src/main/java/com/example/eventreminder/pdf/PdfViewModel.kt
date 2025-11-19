package com.example.eventreminder.pdf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * =============================================================
 * TODO-0 ViewModel:
 * Triggers blank PDF test and exposes URI for UI
 * =============================================================
 */
@HiltViewModel
class PdfViewModel @Inject constructor(
    private val pdfTestGenerator: PdfTestGenerator
) : ViewModel() {

    private val _pdfUri = MutableStateFlow<String?>(null)
    val pdfUri = _pdfUri.asStateFlow()

    fun runBlankPdfTest() {
        viewModelScope.launch {
            val result = pdfTestGenerator.createBlankPdfTest()
            _pdfUri.value = result.getOrNull()
        }
    }
}
