package com.example.eventreminder.data.delivery

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * PdfDeliveryDataStore
 *
 * Responsibility:
 * - Defines DataStore instance for PDF notification delivery ledger.
 *
 * Domain:
 * - Notification delivery tracking only
 * - NOT auth
 * - NOT session
 * - NOT reminders
 */
val Context.pdfDeliveryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pdf_delivery_ledger"
)
