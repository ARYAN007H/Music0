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

        // Lean low-RAM + 120Hz frame-pacing JVM & Skiko profile:
        // - 160 MB max heap with G1PeriodicGCInterval returns idle heap pages to the OS
        // - Compact JIT CodeCache (48 MB) + 2 compiler threads cuts native HotSpot overhead
        // - 32 MB Skiko GPU resource cache prevents Mesa/iGPU texture & path cache bloat
        jvmArgs += listOf(
            "-Xms24m",
            "-Xmx160m",
            "-XX:+UseG1GC",
            "-XX:G1HeapRegionSize=1m",
            "-XX:MaxGCPauseMillis=12",
            "-XX:MaxHeapFreeRatio=20",
            "-XX:MinHeapFreeRatio=5",
            "-XX:G1PeriodicGCInterval=15000",
            "-XX:ParallelGCThreads=2",
            "-XX:ConcGCThreads=1",
            "-XX:CICompilerCount=2",
            "-XX:ReservedCodeCacheSize=48m",
            "-XX:MaxMetaspaceSize=96m",
            "-XX:SoftRefLRUPolicyMSPerMB=50",
            "-XX:+UseStringDeduplication",
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
