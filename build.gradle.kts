// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    // Core Android plugins (applied per-module)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false

    // Kotlin plugins
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false

    // 🔥 Add Kotlin Serialization plugin (required for typed navigation!)
    alias(libs.plugins.kotlin.serialization) apply false

    // Hilt and kapt plugins
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kapt) apply false

    // Firebase plugin (for google-services.json)
    alias(libs.plugins.google.services) apply false
}