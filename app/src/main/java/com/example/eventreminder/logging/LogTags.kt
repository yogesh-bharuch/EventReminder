package com.example.eventreminder.logging

// =============================================================
// Global logging tags used across layers
// =============================================================

// --- Reminder CRUD ---
const val SAVE_TAG = "SaveReminder"
const val DELETE_TAG = "DeleteReminder"

// --- Scheduling / Engine ---
const val ENGINE_TAG = "SchedulingEngine"
const val ELAPSED_TAG = "ElapsedReminder"

// --- Boot / Restore ---
const val BOOT_TAG = "BootRestore"

// --- Maintenance / GC ---
const val GC_TAG = "TombstoneGC"
