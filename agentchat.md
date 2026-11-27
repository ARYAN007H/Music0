# agentchat.md — Music0 active decision & task log

> Operational coordination & product decision log for **Music0** (`v1.0.1`+, Compose Multiplatform + pure-Rust `musico_core`). Obsolete Round-2 Tauri/web-preview worktree rules were trimmed in `v1.0.1` doc cleanup; see [CONTRIBUTING.md](CONTRIBUTING.md) and [docs/development.md](docs/development.md) for current build/test workflows.

---

## Active Product & Architecture Decisions

### 2026-11-22 21:35 UTC — Aryan (Decision Log)
- **Palette naming:** Round-2 palette naming (`Nebula`, `Ocean`, `Ember`, `Forest`, `Mono`) is the intended CMP UI spec.
- **Tauri retirement + v1.0.0 direction:** Tauri (`src-tauri/`) was retired in PR #132 (`fix/retire-tauri-compose-v1`, `v1.0.0`) in favor of **Compose Multiplatform** (`:shared`, `:app`, `:desktopApp`) backed by pure-Rust `musico_core`.
- **T23 Playlist Import:** Both public URL import (`open.spotify.com`, `music.apple.com`) and local file import (CSV/JSON/M3U) are in scope for v1.
- **Decision logging:** Product decisions are recorded here when made.

### 2026-11-22 21:55 UTC — Aryan (Round 5 Directives — `T25` & `T26`)
- **T25 — Instant-start progressive playback (Shipped in PR #133 / `v1.0.1`):**
  - Songs must begin playing as soon as the first audio chunks arrive instead of waiting for whole-track resolution/download.
  - Shipped with `LoopbackHttpMediaSource` (`musico_playback/src/http_source.rs`), `16 KiB` `INITIAL_READY_BYTES` startup gate (`musico_core/src/online/stream_server.rs`), hedged direct-source opening, `yt-dlp -g` desktop progressive fallback, and Media3 `500 ms` `DefaultLoadControl`.
- **T26 — Backdrop v2 ("rain on clear glass", Open / Ongoing Evaluation):**
  - Replace/upgrade the Fluid water effect with a clean rain-on-clear-glass look while respecting bundle-size constraints (evaluate bundled seamless MP4 loop vs Skia/AGSL shader port before deciding).

### 2026-11-22 22:1x UTC — Aryan (Round 5 Directive — `T27`)
- **T27 — Updater UX vs. Google Play Protect (Layer 1 shipped; Layers 2–3 open):**
  - Play Protect can show `"Harmful app blocked"` on sideloaded APK installs/updates despite the APK being signed with the consistent release keystore (`0cd961a9...f12353`) and scanning `0/66` on VirusTotal.
  - **Layer 1 (Shipped):** Documented verification steps (SHA-256 checksums, VirusTotal scan, `apksigner` fingerprint, Play Protect guidance) in [docs/verify-downloads.md](docs/verify-downloads.md) and linked from [README.md](README.md).
  - **Layer 2 (Open):** In-app pre-install info sheet before `UpdateManager` hands off to the Android package installer.
  - **Layer 3 (Open):** Google Play Protect false-positive appeal & reputation accumulation (Play Store distribution remains intentionally deferred due to InnerTube policy risk).

---

## Recent Completed Task Log (CMP `v0.9.6` → `v1.0.1`)

- **T14 (DONE):** Settings stream-cache slider local drag state + `256 MB` snap + `stream::set_cache_cap_override_mb` wiring on `init_core` & `settings.save`.
- **T15 (DONE):** Android AudioFX `MobileEqualizer` session attach/read-back & `PlaybackService` lifecycle fixes; Desktop EQ prefs persistence + conflated `Channel` push to `player.set_eq` + Rust DSP tests (`musico_playback/src/eq.rs`).
- **T17 (DONE):** Shared pluralization formatters (`formatTrackCount`, `formatSongCount`, `formatDownloadsSummary`) across Smart Mixes, Downloads card, DetailScreen, and rail.
- **T18 (DONE):** Fixed negative corner radius crash (`shellRadius.coerceAtLeast(0.dp)`) and clamped animated corners, scales, radial gradients, and zero-size canvases across CMP screens.
- **T19 (DONE):** Desktop keyboard shortcut bubble-phase routing (`onKeyEvent` vs `onPreviewKeyEvent`), `520×560` min resize across `840.dp` breakpoint, responsive dock controls, and packaged native library discovery (`MusicoCore.desktop.kt`).
- **T20 (DONE):** Seamless `Modifier.musicoWash(...)` offscreen cubic alpha feathering, Aurora & Rain backdrop overhauls, and floating `76 dp` M3 Expressive squircle pill dock on Desktop.
- **T21 (DONE):** 12-screen Material 3 Expressive polish audit (`Theme.kt` 5-rung tonal surface ladder, `MusicoSpacing`, `MusicoShapes`, shimmer skeletons, inline retry).
- **T22 (DONE):** Language-to-style home personalization fallback, version sync, `OsRng` 32-hex loopback stream tokens (`stream_server.rs`), `movableContentOf` resize fix, async core prewarm, and parallel JioSaavn-vs-InnerTube race.
- **T23 (DONE):** Zero-OAuth Playlist Import (`musico_core/src/online/playlist_import.rs`, `library.playlist.import` / `.status` / `.cancel`, CSV/JSON/M3U + `open.spotify.com` / `music.apple.com` SSRF-allowlisted URL import).
- **T25 (DONE in `v1.0.1`):** Near-instant progressive playback on Android & Desktop (`http_source.rs`, `16 KiB` startup gate, hedged source opening).
