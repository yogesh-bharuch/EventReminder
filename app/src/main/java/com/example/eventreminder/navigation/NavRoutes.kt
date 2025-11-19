package com.example.eventreminder.navigation

import kotlinx.serialization.Serializable

@Serializable object LoginRoute
@Serializable object HomeRoute
@Serializable object ReminderManagerRoute
@Serializable object DebugScreen

@Serializable data class AddEditReminderRoute(
    val eventId: String? = null
)
