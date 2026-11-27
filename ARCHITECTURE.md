# Architecture

> **Status:** describes Music0 **v1.0.1** (`1.0.1-cmp`, Compose Multiplatform + pure-Rust `musico_core`).
> **Scope:** the production Android and Desktop (Linux, macOS, Windows) Compose Multiplatform application. The standalone browser/PWA demo (`frontend/` + `server.ts`) is documented in [docs/frontend.md](docs/frontend.md).

Music0 is a streaming-capable, local-first music player built on **Kotlin Compose Multiplatform (CMP)** and a **three-crate Rust core** (`musico_core`, `musico_playback`, `musico_recommender`). Tauri (`src-tauri/`) was retired in `v1.0.0` ([ADR-0005](docs/adr/0005-compose-multiplatform-and-jni-core.md)); there is no WebView in any shipped artifact.

---

## 1. System overview

```text
+-----------------------------------------------------------------------+
|  UI Shell — Kotlin Compose Multiplatform (android/shared, :app,       |
|  :desktopApp) — Material 3 Expressive, 4 tabs + 7 overlays, 840dp     |
|  responsive breakpoint                                                |
|  Android: MainActivity.kt + Media3 ExoPlayer + AudioFX MobileEqualizer|
|  Desktop: Main.kt (JVM) + DesktopPlayback (150ms player.state poll)   |
+-----------------------------------+-----------------------------------+
                                    |  JNI JSON-in / JSON-out
                                    |  initNative(filesDir, cacheDir)
                                    |  invoke(cmd, argsJson)  (48 cmds)
+-----------------------------------v-----------------------------------+
|  musico_core (libmusico_online_lib.{so,dylib} / musico_online_lib.dll)|
|  mobile.rs (dispatcher + indexer worker + 4 mobile:* sled trees)      |
|  config.rs · identity.rs · cover.rs · lyrics.rs                       |
|  online/ (InnerTube, JioSaavn, LRCLIB, playlist_import,               |
|           sources.rs hedged direct-URL, stream_server.rs loopback)    |
+---------------+-----------------------------------+-------------------+
                |                                   |
                v (Desktop only)                    v
+-------------------------------+   +-----------------------------------+
| musico_playback               |   | musico_recommender                |
| Symphonia + Rubato + CPAL     |   | Pure-Rust local taste engine      |
| 10-band ISO EQ + tube warmth  |   | 30-dim acoustic features (FFT)    |
| LoopbackHttpMediaSource       |   | 8-signal scoring + MMR + SmartMix |
| (127.0.0.1 progressive read)  |   | sled + bincode (13+ named trees)  |
+-------------------------------+   +-----------------------------------+
```

### Two CMP targets, one Rust core

| Target | Entrypoint | Audio Engine | Native Library |
|---|---|---|---|
| **Android** (`:app`, `arm64-v8a`) | `MainActivity.kt` → `MusicoApp(vm)` | Media3 `ExoPlayer` + `PlaybackService` (`MediaSessionService`) + `MobileEqualizer` | `libmusico_online_lib.so` + `libc++_shared.so` in `jniLibs/arm64-v8a/` |
| **Desktop** (`:desktopApp`, Linux/macOS/Windows) | `desktop/Main.kt` → `MusicoApp(vm)` | `musico_playback::PlaybackEngine` via JNI `player.*` commands (`150 ms` `player.state` poll) | `libmusico_online_lib.so` / `.dylib` / `musico_online_lib.dll` |
| **Web Preview** (unreleased demo) | `server.ts` + `frontend/` | HTML5 `<audio>` + Express mock (`/api/invoke/:cmd`, `/api/audio/:trackId`) | None (TypeScript engine in `src/engine/`) |

---

## 2. Design principles

1. **Zero Tauri / WebView dependencies in Rust.** `musico_core` compiles cleanly as `cdylib`/`staticlib`/`rlib` for Android NDK (`aarch64-linux-android`) and Linux/macOS/Windows hosts ([`musico_core/Cargo.toml`](musico_core/Cargo.toml)).
2. **Panic-safe JNI boundary.** `Java_io_github_aryan007h_musicoonline_core_MusicoCore_invoke` wraps every call in `catch_unwind` and returns `{"ok":true,"data":...}` or `{"ok":false,"error":"..."}` ([`musico_core/src/mobile.rs`](musico_core/src/mobile.rs)).
3. **Progressive playback first.** Interactive plays (`allowStream = true`) open a `127.0.0.1:<port>/stream/<token>` loopback HTTP/1.1 range stream over `.part` with a `16 KiB` startup gate ([`stream_server.rs`](musico_core/src/online/stream_server.rs)), consumed by Media3 on Android and `LoopbackHttpMediaSource` on Desktop ([`http_source.rs`](musico_playback/src/http_source.rs)).
4. **Local-first, account-free state.** History, likes, playlists, downloaded track metadata, and 30-dim acoustic vectors live exclusively on-device in `sled` and disk caches.
5. **Single drift-sensitive parser.** `musico_core/src/online/parse.rs` owns InnerTube JSON shape extraction; the rest of `online/` and Kotlin UI consume typed `OnlineTrack` / `BrowsePage` / `ShelfItem` models.

---

## 3. Repository map

```text
Cargo.toml                  3-crate Rust workspace (strip + thin LTO release profile)
musico_core/                Pure-Rust JNI core (libmusico_online_lib)
  src/lib.rs, mobile.rs     JNI initNative + invoke (48 commands), indexer worker
  src/config.rs, identity.rs, cover.rs, lyrics.rs
  src/online/               InnerTube, JioSaavn, LRCLIB, playlist_import, sources,
                            stream, stream_server (loopback range server)
musico_playback/            Rust audio engine (Desktop JVM + E2E tests)
  src/lib.rs, decoder.rs, output.rs, resampler.rs, eq.rs, crossfade.rs,
      queue.rs, http_source.rs, events.rs, state.rs, error.rs
musico_recommender/         Pure-Rust local recommender + sled persistence
  src/lib.rs, models.rs, recommender.rs, extractor.rs, library.rs,
      user_playlists.rs, adapt.rs, artists.rs, clusters.rs, context.rs,
      diversity.rs, history.rs, likes.rs, profile.rs, session.rs, stats.rs, ...
android/                    Compose Multiplatform project (Gradle 8.10.2, Kotlin 2.1.0, CMP 1.7.3)
  shared/src/commonMain/    Material 3 Expressive screens, MusicViewModel, MusicoCore expect, Models
  shared/src/androidMain/   AndroidPlatform, UpdateManager, PlaybackController, PlaybackService, MobileEqualizer
  shared/src/desktopMain/   DesktopPlatformHost, DesktopPrefs, DesktopPlayback, MusicoCore.desktop.kt
  shared/src/desktopTest/   SharedCoreTests.kt (14 unit tests)
  app/                      Android application module (applicationId: io.github.aryan007h.musicoonline)
  desktopApp/               Desktop JVM Compose module (packageName: Music0)
flatpak/                    Flatpak manifest, desktop entry, AppStream metainfo (io.github.aryan007h.Music0)
scripts/                    package_appimage.sh, package_flatpak.sh, browser_*_test.mjs, contrast_gate.mjs
frontend/, server.ts, src/  Standalone web/PWA demo (not shipped in releases)
docs/                       Technical guides, ADRs, verification guide, brand guidelines, screenshots
.github/workflows/          ci.yml, release.yml, desktop-core-so.yml
```

---

## 4. Process and threading model

- **UI thread (Android Main / Desktop AWT-Swing-Skiko):** Renders Compose state from `MusicViewModel` (`StateFlow`). Never calls `MusicoCore.invoke` directly.
- **Kotlin `Dispatchers.IO`:** `MusicoCore.call()` / `callElement()` dispatches blocking JNI calls off the UI thread.
- **Desktop playback poll (`150 ms`):** `DesktopPlayback` polls `player.state` every `150 ms` to synchronize progress, buffering, EQ, and `SongEnded` auto-advance.
- **Rust audio thread (Desktop only):** CPAL callback reads from Symphonia + Rubato + 10-band biquad EQ / tube-warmth DSP ring buffer.
- **Background acoustic indexer (`musico-mobile-indexer`):** Single Rust worker thread spawned by `recommender()` in `mobile.rs` decodes cached audio files via `musico_recommender::extractor::analyze_file` and populates the 30-dim vector store + `mobile:vid_to_song` / `mobile:song_to_vid`.
- **Stream pump & loopback HTTP server:** `stream_server.rs` listens on `127.0.0.1:0` and spawns a producer pump per active `resolve_track_streaming` session, waking range-request reader threads via a `Condvar` as chunks land in `{stem}.{seq}.part`.

---

## 5. Crate and module boundaries

| Crate | Purpose | Key Dependencies |
|---|---|---|
| `musico_recommender` | Feature extraction, 8-signal + MMR scoring, Smart Mixes, sled store (`songs`, `events`, `sessions`, `scores`, `likes`, `artists`, `context`, `clusters`, `lib:*`, `user_playlists`) | `sled`, `bincode`, `symphonia`, `rustfft` |
| `musico_playback` | Gapless audio decoding, `64 KiB` `MediaSourceStream` buffering, `LoopbackHttpMediaSource`, Rubato resampling, CPAL output, 10-band ISO EQ + stem isolation + tube warmth | `symphonia`, `cpal`, `rubato`, `reqwest` (blocking, for loopback only) |
| `musico_core` | JNI bridge (`mobile.rs`), settings (`config.rs`), identity (`identity.rs`), covers (`cover.rs`), LRC/word-sync (`lyrics.rs`), online stack (`online/*`) | `jni`, `reqwest` (blocking), `serde_json`, `tokio`? (none — pure `std::thread` + blocking HTTP), `rand` (`OsRng`), `cipher`/`des`/`ecb` |

---

## 6. Native JNI & platform contracts

### JNI entrypoints ([`musico_core/src/mobile.rs`](musico_core/src/mobile.rs))
- `Java_io_github_aryan007h_musicoonline_core_MusicoCore_initNative(filesDir, cacheDir)` — creates directories, exports `HOME`, `XDG_DATA_HOME`, `XDG_CONFIG_HOME` = `filesDir` and `XDG_CACHE_HOME` = `cacheDir`, initializes logging (`android_logger` `"musico-native"` or `env_logger`), applies persisted stream-cache cap, warms `MusicRecommender`, `stream_server`, `sources`/`saavn`, and (Desktop) `DESKTOP_PLAYER`.
- `Java_io_github_aryan007h_musicoonline_core_MusicoCore_invoke(cmd, argsJson)` — routes **48** commands across settings, online catalog/search, progressive track resolution, playlists, playlist import, library/likes/downloads, recommender, enhanced lyrics, cache management, GitHub release update checks, and Desktop playback. See [docs/ipc.md](docs/ipc.md) for the complete command catalog.

### Native library loading
- **Android:** `System.loadLibrary("musico_online_lib")` ([`MusicoCore.android.kt`](android/shared/src/androidMain/kotlin/io/github/aryan007h/musicoonline/core/MusicoCore.android.kt)).
- **Desktop JVM:** [`MusicoCore.desktop.kt`](android/shared/src/desktopMain/kotlin/io/github/aryan007h/musicoonline/core/MusicoCore.desktop.kt) searches `MUSICO_NATIVE_LIB` env var → `java.library.path` → `compose.application.resources.dir` / `app.dir` / JAR directory → local Cargo `target/{release,debug}/` paths for `libmusico_online_lib.so` (Linux), `libmusico_online_lib.dylib` (macOS), or `musico_online_lib.dll` (Windows).

---

## 7. Core flows

### 7.1 Interactive playback (`track.resolve` with `allowStream = true`)
1. `mobile.rs` persists track metadata in `mobile:track_meta` and checks `cached_file_for(song_id)` / `cached_file_for(video_id)`. A hit returns the local path immediately (`mode: "cached"`), even in Airplane Mode.
2. Otherwise `resolve_track_streaming` (`musico_core/src/online/stream.rs`) races **JioSaavn cross-source** (`2,500 ms` search cap, `saavn.rs`) in parallel with **Direct-URL hedged streaming** (`InnerTubeDirectSource` first → EWMA-sorted `Piped` → `Invidious`, `900 ms` hedge delay, `sources.rs`). On Desktop, if both miss, it attempts `yt-dlp -g` progressive streaming before whole-file `run_yt_dlp`.
3. Upstream bytes pump to `{stem}.{seq}.part`; once `16 KiB` (`INITIAL_READY_BYTES`) land, `stream_server.rs` returns `http://127.0.0.1:<port>/stream/<32-hex-token>` (`mode: "stream"`).
4. On EOF (`>= 10 KiB`), `.part` renames atomically to the cache path, writes `{stem}.meta.json`, prunes LRU cache to cap, and enqueues `musico-mobile-indexer` acoustic feature extraction.

### 7.2 Smart Mixes & hybrid re-ranking
- `rec.rerank_candidates` blends upstream catalog rank, Rust `artist_affinity`, likes/dislikes, session recency penalty, cache bonus, and an MMR-style artist diversity pass.
- `rec.smart_mixes` generates **Daily Discovery** (8-signal recommender), **On Repeat** (play-history top songs), **Daypart Vibe** (local hour + acoustic RMS energy), and **Offline Ready** (cached tracks).

### 7.3 Zero-OAuth playlist import
- `library.playlist.import` parses exported CSV/JSON/M3U files (up to `4 MiB` / `500` tracks) or fetches public `open.spotify.com` / `music.apple.com` playlist pages (strict HTTPS + exact-host allowlist + `10 s` timeout) in [`playlist_import.rs`](musico_core/src/online/playlist_import.rs), matches tracks against InnerTube search (`200 ms` pacing, cancelable via `library.playlist.import.cancel`), and saves a sled playlist.

---

## 8. State, persistence, and caches

See [docs/data-storage.md](docs/data-storage.md) for the complete layout. Key details:
- Because `DesktopPlatformHost` / Android `MainActivity` pass `musico-online` / app-private directories into `MusicoCore.init()` and `identity.rs` joins `DATA_SUBDIR` (`"musico-online"`) again, Rust files (`db/`, `settings.json`, `online/`, `covers/`) reside in a double-nested `musico-online/musico-online/` folder on Desktop Linux/macOS/Windows (`settings.json` inside `dataDir`, not `~/.config/musico-online/`).
- Compose UI preferences live separately in `<dataDir>/ui_prefs.json` (Desktop) or `SharedPreferences("musico_ui")` (Android).
- Key env overrides: `MUSICO_NATIVE_LIB`, `MUSICO_ONLINE_CACHE_MB`, `MUSICO_PIPED_INSTANCES`, `MUSICO_INVIDIOUS_INSTANCES`, `MUSICO_INNERTUBE_DIRECT` (blank disables), `MUSICO_SAAVN_API`, `MUSICO_VERSION_OVERRIDE`, `RUST_LOG`.

---

## 9. CI, packaging, and known limitations

- **CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)):** `rust-core` (`cargo test --workspace --exclude musico_playback` + `cargo build --release -p musico_core --lib`), `compose-multiplatform` (`gradle -p android :app:compileDebugKotlin :desktopApp:compileKotlin :shared:desktopTest`), `web-engine` (`npm test` + `npm run build`).
- **Release ([`.github/workflows/release.yml`](.github/workflows/release.yml)):** Linux (`.deb`, `.rpm`, `.AppImage`, `.flatpak` bundling `yt-dlp` + `yt-dlp-ejs` + `deno`, `.tar.gz`), macOS arm64 (`.dmg`, `.app.tar.gz`, unsigned), Windows x86_64 (`.msi`, `.zip`, unsigned), signed Android `arm64-v8a` APK (`musico-online-native-aarch64.apk`), and `SHA256SUMS.txt`.
- **Known Desktop gaps:** No single-instance window lock yet on Compose Desktop (a second launch hits sled's file lock and runs without DB), and Desktop MPRIS / SMTC / macOS Now Playing hardware media controls are not yet wired (tracked in [ROADMAP.md](ROADMAP.md)).
