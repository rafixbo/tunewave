// TuneWave — top-level build file.
// Proven toolchain from After Class Kotlin v7 (CI green 2026-11-26):
// Kotlin 2.2.21 + AGP 8.7.3 + Gradle 8.12. The 2.2.21 compiler cannot read
// Kotlin 2.3.x metadata, so kotlin-stdlib is pinned to 2.2.21 below.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21" apply false
}

allprojects {
    configurations.configureEach {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.21")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.21")
        }
    }
}
