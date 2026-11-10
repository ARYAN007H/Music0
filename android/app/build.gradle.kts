plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// versionCode/versionName: CI overrides versionCode per release
// (-PmusicoVersionCode / -PmusicoVersionName); defaults keep local
// `gradle assembleDebug` working without inputs.
val musicoVersionCode: Int = (project.findProperty("musicoVersionCode") as String?)?.toInt() ?: 90000
val musicoVersionName: String = project.findProperty("musicoVersionName") as String? ?: "0.9.0-native"

android {
    namespace = "io.github.aryan007h.musicoonline"
    compileSdk = 35

    defaultConfig {
        // Same applicationId as the Tauri APK: installing the native build
        // replaces it (until the Tauri android job retires at parity, #111).
        applicationId = "io.github.aryan007h.musicoonline"
        minSdk = 26
        targetSdk = 35
        versionCode = musicoVersionCode
        versionName = musicoVersionName
    }

    // CI signs with the shared release keystore (ANDROID_KEYSTORE_* secrets,
    // same key as the Tauri APKs so installs stay compatible); unsigned for
    // local debug builds.
    signingConfigs {
        create("ciRelease") {
            val ks = System.getenv("ANDROID_KEYSTORE_FILE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val ks = System.getenv("ANDROID_KEYSTORE_FILE")
            signingConfig = if (ks != null) signingConfigs.getByName("ciRelease") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.guava)
}
