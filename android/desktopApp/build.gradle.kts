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

compose.desktop {
    application {
        mainClass = "io.github.aryan007h.musicoonline.desktop.MainKt"

        // Tuned low-RAM JVM profile (~110-165 MB typical RSS with G1GC heap shrinking).
        jvmArgs += listOf(
            "-Xmx256m",
            "-XX:+UseG1GC",
            "-XX:MaxHeapFreeRatio=30",
            "-XX:MinHeapFreeRatio=10",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Music0"
            // jpackage Dmg validation requires MAJOR >= 1 (semantic app version is 0.9.6).
            packageVersion = "1.0.0"
            description = "Music0 — Expressive Material 3 Music Player"
            vendor = "ARYAN007H"
        }
    }
}
