package com.example.eventreminder.navigation

import kotlinx.serialization.Serializable

@Serializable object LoginRoute
@Serializable object HomeRoute
@Serializable object ReminderManagerRoute
@Serializable object DebugRoute
@Serializable
data class CardDebugRoute(
    val reminderId: Long,
    val eventType: String
)

@Serializable data class AddEditReminderRoute(
    val eventId: String? = null
)
