package com.example.eventreminder.navigation

import kotlinx.serialization.Serializable

@Serializable object LoginRoute
@Serializable object HomeRoute
@Serializable object ReminderManagerRoute
@Serializable object DebugScreen
@Serializable object CardDebugScreen

@Serializable data class AddEditReminderRoute(
    val eventId: String? = null
)
