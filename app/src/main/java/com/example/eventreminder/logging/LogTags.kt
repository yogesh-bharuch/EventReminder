package com.example.eventreminder.logging

// =============================================================
// Global logging tags used across layers
//
// RULE (MANDATORY):
// Every Timber log message MUST include
// [FileName.kt::FunctionName] at the END.
// =============================================================

// -------------------------------------------------------------
// AUTH / LOGIN / VERIFICATION
// -------------------------------------------------------------
const val AUTH_STATE_TAG = "AuthState"

// -------------------------------------------------------------
// Reminder CRUD
// -------------------------------------------------------------
const val SAVE_TAG = "SaveReminder"
const val DELETE_TAG = "DeleteReminder"
const val DISMISS_TAG = "DismissReminder"
const val BOOT_RECEIVER_TAG = "BootReceiverReminder"
const val RESTORE_NOT_DISMISSED_TAG = "NotificationRestore"
const val SYNC_TAG = "SyncReminders"
const val DEBUG_TAG = "DebugTag"
const val SHARE_PDF_TAG = "SharePdf"
const val CLEANUP_PDF_TAG = "CleanUpTempFiles"
const val SHARE_LOGIN_TAG = "LoginTag"

// -------------------------------------------------------------
// Scheduling / Engine
// -------------------------------------------------------------
const val ENGINE_TAG = "SchedulingEngine"
const val ELAPSED_TAG = "ElapsedReminder"

// -------------------------------------------------------------
// Boot / Restore
// -------------------------------------------------------------
const val BOOT_TAG = "BootRestore"

// -------------------------------------------------------------
// Maintenance / GC
// -------------------------------------------------------------
const val GC_TAG = "TombstoneGC"
