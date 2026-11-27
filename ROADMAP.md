# Roadmap (Post-v1.0.1)

Music0 reached **`v1.0.1`** with a unified **Compose Multiplatform** UI (`:shared`, `:app`, `:desktopApp`), pure-Rust JNI core (`musico_core`), progressive `16 KiB` loopback HTTP/1.1 streaming (`stream_server.rs` + `LoopbackHttpMediaSource`), parallel JioSaavn-vs-Direct resolution, zero-OAuth playlist import, 8-signal local recommender + Smart Mixes, word-synced Lyrics Studio, and Linux AppImage/Flatpak packaging (`yt-dlp` + `yt-dlp-ejs` + `deno`). Tauri (`src-tauri/`) was retired in `v1.0.0`.

This document tracks what comes **after `v1.0.1`**.

---

## 1. Near-Term Polish & Open Decisions (from `v1.0.x`)

- **T26 — Backdrop v2 ("Rain on clear glass"):**
  - Evaluate bundle-size and rendering tradeoffs (bundled seamless loop vs. Skia/AGSL shader vs. refined procedural canvas) to replace the Fluid water effect with a clean rain-on-clear-glass backdrop across Desktop and Android.
- **T27 — Android Updater UX & Play Protect Reputation (Layers 2–3):**
  - **Layer 2:** Add an in-app pre-install info sheet before `UpdateManager.kt` invokes the Android package installer explaining the Play Protect prompt and linking checksum/signing verification ([docs/verify-downloads.md](docs/verify-downloads.md)).
  - **Layer 3:** Track Google Play Protect false-positive appeal clearance across releases.
- **Material 3 Expressive Consistency:**
  - Enforce Material 3 Expressive components, `MusicoSpacing`, `MusicoShapes`, and `Theme.kt` tonal ladders across all new screens and dialogs ([docs/brand/BRAND_GUIDELINES.md](docs/brand/BRAND_GUIDELINES.md)), and expose remaining `AppSettings` options (e.g., cross-source matching / cache details) cleanly in `SettingsScreen.kt`.

---

## 2. Desktop Compose Multiplatform Parity

- **Single-instance guard on Desktop (`android/desktopApp/`):**
  - Prevent a second Desktop launch from racing the `sled` file lock (`db/`) by focusing the existing window over a local lock/socket (restoring parity with retired Tauri single-instance behavior).
- **Native Desktop Media Keys & OS Integration:**
  - **Linux:** Wire `org.mpris.MediaPlayer2.musico-online` over D-Bus (`flatpak/io.github.aryan007h.Music0.yml` already reserves `--own-name=org.mpris.MediaPlayer2.musico-online`).
  - **Windows / macOS:** Integrate SMTC and macOS Now Playing media-key / transport metadata controls.
- **Desktop Self-Updater & Code Signing:**
  - Upgrade Desktop update flow beyond opening the GitHub release URL in a browser, and explore code signing for macOS (`.dmg`) and Windows (`.msi`) releases when certificates are available.

---

## 3. Core Engine, Storage & Streaming Reliability

- **Storage path normalization (`identity.rs` + `DesktopPlatformHost` / `MainActivity`):**
  - Cleanly migrate or resolve the current `musico-online/musico-online/` double-nested directory layout (where both Kotlin and `identity.rs` append `DATA_SUBDIR`) without losing existing user `sled` databases, `settings.json`, or cached downloads.
- **Sled `events` retention & compaction:**
  - Add bounded retention / periodic compaction for the append-only `events` tree in `musico_recommender` so long-lived installs stay small.
- **Per-track / settings override for JioSaavn cross-source matching:**
  - Allow users to disable or re-resolve a track via YouTube-only if the JioSaavn cross-source heuristic picks an alternate take/version.
- **Desktop crossfade & DSP exposure:**
  - Wire `musico_playback`'s existing crossfade curves (`crossfade.rs`) and stem/tube-warmth toggles (`AppSettings`) cleanly into the Compose Desktop & Settings UI.
- **Expanded automated CMP UI testing:**
  - Add Compose UI tests alongside `SharedCoreTests.kt` (`14` unit tests) to exercise navigation, responsive `840.dp` transitions, and player overlay state in CI.

---

## 4. Deliberate Non-Goals

- **No cloud accounts, telemetry, or remote taste sync:** Listening history, likes, playlists, and acoustic feature vectors stay strictly on-device.
- **No Play Store release with InnerTube extraction:** Distribution remains direct GitHub Releases (APK + desktop packages) to avoid developer-account enforcement risks.
- **No revival of Tauri or `frontend/` as the production shell:** `frontend/` + `server.ts` remains only a standalone web preview demo.
