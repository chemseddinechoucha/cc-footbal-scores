// app/build.gradle.kts  (module-level)
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")        // Kotlin 2.x Compose compiler plugin
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Read the API key from local.properties (never commit this file):
//   FOOTBALL_API_KEY=your_key_here
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "org.ccdev.footballscores"
    compileSdk = 36

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(
                localProps.getProperty("RELEASE_STORE_FILE")
                    ?: error("RELEASE_STORE_FILE is missing from local.properties"),
            )
            storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                ?: error("RELEASE_STORE_PASSWORD is missing from local.properties")
            keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                ?: error("RELEASE_KEY_ALIAS is missing from local.properties")
            keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
                ?: error("RELEASE_KEY_PASSWORD is missing from local.properties")
        }
    }

    defaultConfig {
        applicationId = "org.ccdev.footballscores"
        minSdk = 26            // java.time works natively, no desugaring needed
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "FOOTBALL_API_KEY", "\"${localProps.getProperty("FOOTBALL_API_KEY", "")}\"")
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Team crests (football-data.org serves a mix of PNG and SVG)
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("io.coil-kt.coil3:coil-svg:3.3.0")
}
// Versions are examples: bump to the latest stable in Android Studio.
