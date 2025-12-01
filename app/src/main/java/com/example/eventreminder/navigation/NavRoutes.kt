package com.example.eventreminder.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LoginRoute

@Serializable
data object HomeRoute

@Serializable
data object ReminderManagerRoute

@Serializable
data class CardDebugRoute(
    val reminderId: Long,
    val eventType: String
)

@Serializable
data class AddEditReminderRoute(
    val eventId: String? = null
)

@Serializable
data class CardRoute(
    val reminderId: Long
)

@Serializable
data class PixelPreviewRoute(
    val reminderId: Long
)


/*
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
*/
