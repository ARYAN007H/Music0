plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val musicoVersionCode: Int = (project.findProperty("musicoVersionCode") as String?)?.toInt() ?: 1000000
val musicoVersionName: String = project.findProperty("musicoVersionName") as String? ?: "1.0.0-cmp"

android {
    namespace = "io.github.aryan007h.musicoonline"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.aryan007h.musicoonline"
        minSdk = 26
        targetSdk = 35
        versionCode = musicoVersionCode
        versionName = musicoVersionName
    }

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
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
}
