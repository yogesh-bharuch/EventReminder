package com.example.eventreminder.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LoginRoute

@Serializable
object SplashRoute

@Serializable
data object HomeRoute

@Serializable
data object ReminderManagerRoute

@Serializable
data object HomeGraphRoute


@Serializable
data class AddEditReminderRoute(
    val eventId: String? = null
)


@Serializable
data class PixelPreviewRoute(
    val reminderId: String
)

// ------------------------------------------------------------
// idString parallel route
// ------------------------------------------------------------
@Serializable
data class PixelPreviewRouteString(                      // idchanged to idstring
    val reminderIdString: String                         // idchanged to idstring
)
