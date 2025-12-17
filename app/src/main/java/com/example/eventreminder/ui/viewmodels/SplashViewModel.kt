package com.example.eventreminder.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.eventreminder.data.repo.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    suspend fun normalizeDatabase() {
        reminderRepository.normalizeRepeatRules()
    }
}
