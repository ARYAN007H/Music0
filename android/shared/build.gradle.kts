import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    jvm("desktop") {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.animation)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.okhttp)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.session)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.kotlinx.coroutines.guava)
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "io.github.aryan007h.musicoonline.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
