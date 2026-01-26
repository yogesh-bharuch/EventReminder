package com.example.eventreminder.data.delivery

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * PdfDeliveryLedger
 *
 * Responsibility:
 * - Contract for tracking PDF notification delivery state.
 *
 * Guarantees:
 * - Single source of truth for delivery state
 * - Prevents duplicate notifications
 * - Deterministic delivery behavior
 *
 * Domain:
 * - Notification delivery only
 */
interface PdfDeliveryLedger {

    /**
     * Stream of last delivered Next7Days PDF date.
     *
     * Return:
     * - Flow<LocalDate?>  (null = never delivered)
     */
    val next7DaysLastDelivered: Flow<LocalDate?>

    /**
     * Mark Next7Days PDF as delivered today.
     *
     * Caller(s):
     * - Worker
     * - Coordinator
     *
     * Return:
     * - Unit
     */
    suspend fun markNext7DaysDelivered(date: LocalDate)

    /**
     * Clear delivery state.
     *
     * Caller(s):
     * - Debug tools
     * - Recovery flows
     *
     * Return:
     * - Unit
     */
    suspend fun clearNext7DaysDelivery()
}
