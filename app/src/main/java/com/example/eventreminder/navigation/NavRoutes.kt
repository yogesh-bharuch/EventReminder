package com.example.eventreminder.navigation

import kotlinx.serialization.Serializable

// =============================================================
// AUTH / ENTRY ROUTES
// =============================================================

@Serializable
data object SplashRoute

@Serializable
data object LoginRoute

@Serializable
data object EmailVerificationRoute   // ✅ NEW — email verification gate

// =============================================================
// HOME GRAPH
// =============================================================

@Serializable
data object HomeGraphRoute

@Serializable
data object HomeRoute

@Serializable
data object ReminderManagerRoute

// =============================================================
// ADD / EDIT
// =============================================================

@Serializable
data class AddEditReminderRoute(
    val eventId: String? = null
)

// =============================================================
// PIXEL PREVIEW
// =============================================================

@Serializable
data class PixelPreviewRoute(
    val reminderId: String
)

// ------------------------------------------------------------
// UUID-string parallel route
// ------------------------------------------------------------
@Serializable
data class PixelPreviewRouteString(
    val reminderIdString: String
)

// =============================================================
// DEBUG
// =============================================================

@Serializable
data object SchedulingDebugRoute
