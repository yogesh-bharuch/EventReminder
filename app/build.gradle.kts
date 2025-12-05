plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    kotlin("kapt")
    alias(libs.plugins.google.services) // ✅ Required for Firebase initialization
}

android {
    namespace = "com.example.eventreminder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.eventreminder"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // 🧪 Enable test button and debug-only features
            buildConfigField("boolean", "SHOW_TEST_BUTTON", "true")
            isDebuggable = true
        }
        release {
            // 🚫 Disable test button in production
            buildConfigField("boolean", "SHOW_TEST_BUTTON", "false")

            // 🔐 Enable code shrinking and obfuscation (optional)
            isMinifyEnabled = true

            // 📦 Use default and custom ProGuard rules
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
}

dependencies {
    // ✅ Jetpack Compose essentials
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ✅ Core AndroidX libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Material Icons (Filled)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.room.ktx)

    // ✅ Firebase login module
    implementation(project(":firebaseloginmodule"))
    //implementation(libs.support.annotations)
    implementation(libs.firebase.auth.ktx) // ✅ Required for FirebaseAuth in host module
    implementation(libs.firebase.firestore.ktx)

    // Room
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.runtime.saveable)
    implementation(libs.androidx.junit.ktx)
    kapt(libs.androidx.room.compiler)

    // ✅ Hilt DI
    implementation(libs.hilt.android)
    //implementation(libs.androidx.work.runtime.ktx)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ✅ WorkManager (core + KTX)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlin.reflect)

    // 🩵 FIX for ListenableFuture error
    implementation(libs.guava)
    implementation(libs.kotlinx.coroutines.guava)

    implementation(libs.itextg)

    implementation(libs.kotlinx.serialization.json)

    // ✅ Jetpack Navigation Compose
    implementation(libs.navigation.compose)

    // ✅ Unit testing
    testImplementation(libs.junit)

    // ✅ Instrumentation testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // ✅ Debug-only tools
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.timber)
    implementation(kotlin("test"))

    // ----------- Unit Tests (Robolectric / JVM) -----------
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)

// ----------- Android Instrumented Tests -----------
    androidTestImplementation(libs.androidx.junit.v115)
    androidTestImplementation(libs.androidx.espresso.core.v351)

// Required for Canvas & Bitmap tests in instrumented mode
    androidTestImplementation(libs.androidx.core)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)


}