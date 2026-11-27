import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
}

val musicoVersionName: String = (project.findProperty("musicoVersionName") as String?) ?: "1.0.1"
val musicoPackageVersion: String = musicoVersionName
    .removePrefix("v")
    .substringBefore('-')
    .ifBlank { "1.0.1" }

compose.desktop {
    application {
        mainClass = "io.github.aryan007h.musicoonline.desktop.MainKt"

        // Tuned low-RAM + 120Hz frame-pacing JVM profile (sub-5ms G1GC pause target).
        jvmArgs += listOf(
            "-Xmx384m",
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=5",
            "-XX:MaxHeapFreeRatio=30",
            "-XX:MinHeapFreeRatio=10",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "Music0"
            packageVersion = musicoPackageVersion
            description = "Music0 — Expressive Material 3 Music Player"
            vendor = "ARYAN007H"
            copyright = "Copyright (c) 2026 ARYAN007H. MIT License."

            // Bundles platform-specific Rust JNI library (libmusico_online_lib.{so,dylib,dll})
            // from resources/{linux,macos,windows}/ into `compose.application.resources.dir`.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            // Include full standard JDK modules so TLS/OkHttp/Coil3/AWT never hit missing-module
            // errors inside jlink-trimmed native distributions.
            includeAllModules = true

            linux {
                packageName = "music0"
                debMaintainer = "ARYAN007H"
                menuGroup = "AudioVideo;Audio;Player;"
                appCategory = "AudioVideo"
                iconFile.set(project.file("icons/icon.png"))
            }

            macOS {
                bundleID = "io.github.aryan007h.musicoonline"
                dockName = "Music0"
                iconFile.set(project.file("icons/icon.icns"))
            }

            windows {
                menuGroup = "Music0"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "8d3e4c92-5c7b-4f1b-a91e-7a5e2f90c314"
                iconFile.set(project.file("icons/icon.ico"))
            }
        }
    }
}
