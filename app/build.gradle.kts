plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.rafixbo.tunewave"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rafixbo.tunewave"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0+1"
    }

    buildTypes {
        release {
            // v1 ships unsigned from CI (no keystore committed, ever).
            // Signing happens outside Gradle if/when Rafi wants a
            // release key. Debuggable is false (Gradle default for
            // release) — stated explicitly so it can never regress.
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
        }
        debug {
            isMinifyEnabled = false
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

    lint {
        // Same AGP 8.7.3 / Kotlin 2.2.21 lint-tooling bug as After Class
        // Kotlin v6 (CI run 44881342690): lintVitalAnalyzeRelease crashes
        // in NonNullableMutableLiveDataDetector. Not app code.
        disable += "NullSafeMutableLiveData"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM 2025.05.01 (May 2025 era — Kotlin 2.1/2.2 metadata,
    // safe with the 2.2.21 compiler) + Material 3 1.3.2 pinned for
    // Material 3 Expressive (stable since 1.3.0).
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.9.0")

    // Networking + JSON.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Local settings (DataStore Preferences, no Room in v1 — see README:
    // library/playlists/history/downloads persist as JSON via DataStore
    // + app files, which keeps the v1 build green without a KSP plugin).
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Playback: Media3 ExoPlayer + MediaSession (background audio).
    implementation("androidx.media3:media3-exoplayer:1.8.1")
    implementation("androidx.media3:media3-session:1.8.1")
    implementation("androidx.media3:media3-common:1.8.1")
    implementation("androidx.media3:media3-datasource:1.8.1")

    // Images at display size, bounded cache (proven 3.4.0 in After Class Kotlin v7).
    implementation("io.coil-kt.coil3:coil-compose:3.4.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.4.0")

    // YouTube backend — NO login, NO API key. NewPipe Extractor 0.27.1
    // (verified latest stable, 2026-11-10) via JitPack. Audio-only
    // streams from the extractor carry no ads — TuneWave plays no ads,
    // loads no ad URLs, and ships NO ad SDK (no AdMob / ad network).
    implementation("com.github.TeamNewPipe:NewPipeExtractor:0.27.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
