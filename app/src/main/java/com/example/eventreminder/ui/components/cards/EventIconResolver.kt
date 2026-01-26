package com.example.eventreminder.ui.components.cards

// =============================================================
// Imports
// =============================================================
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * -------------------------------------------------------------
 * EventIconResolver
 * -------------------------------------------------------------
 * Returns a Material icon based on keywords found in title/desc.
 *
 * Used in:
 *  - EventCard
 *
 * Extendable:
 *  - Add more categories
 *  - Add user-defined mappings later
 */
object EventIconResolver {

    fun pickEventIcon(title: String?, desc: String?): ImageVector {
        val text = (title.orEmpty() + " " + desc.orEmpty()).lowercase()

        return when {
            // 🎂 Birthday
            "birthday" in text || "bday" in text ->
                Icons.Filled.Cake

            // ❤️ Anniversary
            "anniversary" in text ->
                Icons.Filled.Favorite

            // 💊 Medicines
            listOf("medicine", "med", "tablet", "pill", "capsule", "dose")
                .any { it in text } ->
                Icons.Filled.Medication   // ✅ replaced MedicationLiquid with Medication

            // 📅 Meeting
            listOf("meeting", "call", "conference", "appointment")
                .any { it in text } ->
                Icons.Filled.CalendarToday

            // 🎉 Party
            listOf("party", "celebration", "event")
                .any { it in text } ->
                Icons.Filled.Celebration

            // 🛒 Shopping
            listOf("shopping", "grocery", "buy")
                .any { it in text } ->
                Icons.Filled.ShoppingCart

            // 💼 Work
            listOf("work", "office", "task")
                .any { it in text } ->
                Icons.Filled.Work

            // 🔔 Default icon fallback
            else -> Icons.Filled.Notifications
        }
    }
}