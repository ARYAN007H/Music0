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
    implementation(libs.kotlinx.coroutines.swing)
}

val musicoVersionName: String = (project.findProperty("musicoVersionName") as String?) ?: "1.0.2"
val musicoPackageVersion: String = musicoVersionName
    .removePrefix("v")
    .substringBefore('-')
    .ifBlank { "1.0.2" }

compose.desktop {
    application {
        mainClass = "io.github.aryan007h.musicoonline.desktop.MainKt"

        // Verified ultra-low-RAM JVM & Skiko profile for a ~25-35 MB live heap:
        // - SerialGC cuts HotSpot GC native metadata from ~43 MB (G1GC) to ~127 KB
        // - C1-only JIT (TieredStopAtLevel=1) + 16 MB CodeCache eliminates C2 arena & code bloat
        // - 32 MB Skiko GPU resource cache prevents Mesa/iGPU texture & path cache bloat
        jvmArgs += listOf(
            "-Xms16m",
            "-Xmx128m",
            "-XX:+UseSerialGC",
            "-XX:TieredStopAtLevel=1",
            "-XX:ReservedCodeCacheSize=16m",
            "-XX:MaxMetaspaceSize=80m",
            "-XX:SoftRefLRUPolicyMSPerMB=25",
            "-XX:MaxHeapFreeRatio=20",
            "-XX:MinHeapFreeRatio=5",
            "-Dskiko.gpu.resourceCacheLimit=33554432",
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
