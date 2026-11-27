# 0005. Compose Multiplatform UI over a pure-Rust JNI core

- Status: Accepted
- Recorded: 2026-11-27
- Deciders: maintainers

> **Amendment (PR #138):** The legacy `frontend/` + `server.ts` web/PWA preview noted in the original decision below was subsequently retired. Compose Multiplatform (`android/shared/`, `:app`, `:desktopApp`) is now the sole UI in the repository.

## Context

Under [ADR-0001](0001-tauri-2-no-build-frontend.md), Musico Online shipped a Tauri 2 shell wrapping a static HTML/CSS/JS frontend (`frontend/`) in platform WebViews (`WebKitGTK`, `WKWebView`, `WebView2`). As the app grew across Desktop and Android:

- Android WebView playback, media notifications, Equalizer AudioFX, background service lifecycle, and gesture/keyboard polish fought the WebView boundary.
- Maintaining two UI stacks (a native Android rewrite introduced in `v0.9.0`–`v0.9.5` alongside the Tauri WebView desktop app) duplicated feature work and caused visual and behavioral drift.
- Tauri coupled the Rust core (`musico_recommender`, `musico_playback`, `online/`) to `tauri` / `tokio` / WebView plugins.

## Decision

Retire Tauri (`src-tauri/`) completely in `v1.0.0` and standardize both **Android** and **Desktop** (Linux, macOS, Windows) on **Kotlin Compose Multiplatform (CMP)** backed by a **pure-Rust JNI core** (`musico_core`):

- **UI layer (`android/shared`, `android/app`, `android/desktopApp`):**
  - `:shared` hosts common Material 3 Expressive screens, `MusicViewModel`, and `expect/actual` platform abstractions (`MusicoCore`, `PlatformPrefs`, `PlatformUpdater`, `Playback`).
  - `:app` hosts the Android `MainActivity`, Media3 `ExoPlayer` + `PlaybackService`, and AudioFX `MobileEqualizer`.
  - `:desktopApp` hosts the Compose Desktop `Window` entrypoint (`Main.kt`) and delegates audio playback to `musico_playback` over JNI (`DesktopPlayback`, polling `player.state` every `150 ms`).
- **Rust core (`musico_core` → `libmusico_online_lib`):**
  - Contains zero Tauri or WebView dependencies.
  - Exposes two JNI entrypoints in `musico_core/src/mobile.rs`: `initNative(filesDir, cacheDir)` and a panic-safe JSON-in/JSON-out `invoke(cmd, argsJson)` dispatcher (**48** commands covering settings, online catalog/search, track resolution, playlists, playlist import, library/likes/downloads, recommender, lyrics, cache, updates, and desktop playback).
- **Legacy `frontend/` + `server.ts`:**
  - Retained in the repository solely as a standalone Node/PWA web preview (`npm start`), not shipped in any release artifact.

## Consequences

Positive:

- Single Kotlin Compose Multiplatform UI codebase across Android, Linux, macOS, and Windows with Material 3 Expressive theming, motion, and responsive layouts (`840.dp` breakpoint).
- Native Android Media3 session/notification/AudioFX integration and direct Rust `musico_playback` audio output on Desktop without WebView audio/IPC bottlenecks.
- Clean separation between pure-Rust domain/streaming/DSP crates (`musico_core`, `musico_playback`, `musico_recommender`) and the UI shell.

Negative:

- Contributors now need a JDK 17 + Gradle `8.10.2` toolchain alongside Rust `cargo` (and Android SDK / NDK for Android builds).
- JNI crosses a JSON string boundary per call (no Rust-to-UI push events; Desktop playback state is polled every `150 ms`).
- Desktop CMP does not yet implement Tauri's single-instance window lock or native MPRIS/SMTC media-key D-Bus bindings (tracked in [ROADMAP.md](../../ROADMAP.md)).

## Alternatives considered

- **Keep Tauri 2 on Desktop and Kotlin Compose on Android.** Rejected: doubled UI maintenance and diverged features/visuals between platforms.
- **Tauri 2 mobile on Android.** Rejected: WebView audio lifecycle, MediaSession, and native Android UX fell short of native Media3 + Compose.
- **C-ABI (`extern "C"`) FFI with JNA/Panama instead of JNI.** Rejected: JNI works identically on Android ART and Desktop HotSpot JVM without additional runtime dependencies.

## References

- `musico_core/src/mobile.rs`
- `android/shared/src/commonMain/kotlin/io/github/aryan007h/musicoonline/core/MusicoCore.kt`
- `android/desktopApp/src/main/kotlin/io/github/aryan007h/musicoonline/desktop/Main.kt`
- `android/app/src/main/java/io/github/aryan007h/musicoonline/MainActivity.kt`
- [ARCHITECTURE.md](../../ARCHITECTURE.md)
- [docs/ipc.md](../ipc.md)
