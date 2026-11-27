# Development

This guide gets a working development environment and explains the tasks you will do most often. It assumes the repository root as the working directory. For the workflow (branches, PRs, review), read [CONTRIBUTING.md](../CONTRIBUTING.md). For the system design, read [ARCHITECTURE.md](../ARCHITECTURE.md).

## 1. Prerequisites

| Tool | Version | Why |
|---|---|---|
| Rust | stable, MSRV 1.78 | Workspace crates (`musico_core`, `musico_playback`, `musico_recommender`) |
| Cargo | bundled with Rust | Builds and tests |
| Java & Gradle | JDK 17+, Gradle 8.10.2 (system `gradle`) | Compose Multiplatform Android & Desktop (`android/` has no wrapper) |
| Node.js | 20+ recommended | Web preview (`server.ts`), TypeScript engine (`src/engine/`), browser suites |
| `yt-dlp` + `deno` | current | Last-resort stream resolver on Desktop (bundled inside Flatpak) |
| `playwright` + Chrome | current | Browser suites only (not a runtime dependency) |

### Linux system packages

CI installs this minimal set (no WebKitGTK/GTK packages required):

```sh
sudo apt-get update
sudo apt-get install -y libasound2-dev pkg-config
```

`libasound2-dev` is required by CPAL. A running PipeWire/PulseAudio server is needed to *run* desktop audio playback, but not to compile it.

### macOS

Install Xcode Command Line Tools (`xcode-select --install`) and Java 17+. Release builds target Apple Silicon (`macos-arm64`).

### Windows

Install the MSVC build tools, Rust, and Java 17+. The release workflow builds with the `x86_64-pc-windows-msvc` target (producing `musico_online_lib.dll`).

### Android

Cross-compile `musico_core` for `arm64-v8a` using `cargo-ndk` (with Android SDK/NDK configured), then assemble the debug APK:

```sh
(cd musico_core && cargo ndk -t arm64-v8a -o ../android/app/src/main/jniLibs build --release --lib)
gradle -p android :app:assembleDebug
```

Android uses the pure-Rust resolvers (InnerTube Direct, Piped, Invidious, JioSaavn) because it cannot spawn `yt-dlp`.

## 2. First build

```sh
cargo build --release -p musico_core --lib   # JNI core cdylib
cargo test --workspace --exclude musico_playback
gradle -p android :desktopApp:run            # Compose Desktop
npm install                                  # web preview + TypeScript tests
```

If `cargo` cannot find a linker or `pkg-config`, install the system packages above before retrying. Do not add vendored binaries to the repository.

## 3. Running the app

### Desktop (Compose Multiplatform)

```sh
cargo build --release -p musico_core --lib
gradle -p android :desktopApp:run
```

`MusicoCore.desktop.kt` locates the native library (`libmusico_online_lib.so` on Linux, `libmusico_online_lib.dylib` on macOS, `musico_online_lib.dll` on Windows) in this order:
1. `MUSICO_NATIVE_LIB=/path/to/lib` environment override
2. `System.loadLibrary("musico_online_lib")` (`java.library.path`)
3. Bundled app directories (`compose.application.resources.dir`, `app.dir`, JAR dir / `lib/`)
4. Local Cargo output directories (`musico_core/target/{release,debug}/`, `target/{release,debug}/`)

### Staging for packaged Desktop installers

`android/desktopApp/build.gradle.kts` bundles native libraries placed in `android/desktopApp/resources/{linux,macos,windows}/` into the packaged `.deb` / `.rpm` / `.dmg` / `.msi` / `.AppImage` / `.flatpak` output. On Linux, `scripts/package_appimage.sh` and `scripts/package_flatpak.sh` build portable AppImage and Flatpak bundles (see [flatpak.md](flatpak.md) and [release.md](release.md)).

### Web / PWA preview (unreleased demo)

```sh
npm run dev              # http://localhost:3000
PORT=8143 npm run dev    # browser-suite port
```

`server.ts` serves `frontend/` and implements a demo RPC backend with a seeded catalog and synthesized audio. It is useful for legacy web-preview UI/PWA work, not for verifying Compose Multiplatform or real Rust stream resolution. It binds `0.0.0.0`, so never expose it to an untrusted network.

## 4. Configuration

### Files (Linux Desktop example)

`Main.kt` passes `DesktopPlatformHost.dataDir()` (`~/.local/share/musico-online`) and `cacheDir()` (`~/.cache/musico-online`) into `MusicoCore.init()`. `mobile.rs` sets `HOME`, `XDG_DATA_HOME`, `XDG_CONFIG_HOME` = `filesDir` and `XDG_CACHE_HOME` = `cacheDir`, and `musico_core/src/identity.rs` joins `DATA_SUBDIR` (`"musico-online"`). As a result, Rust data currently sits in a nested `musico-online/musico-online/` folder:

| Path | Purpose |
|---|---|
| `~/.local/share/musico-online/ui_prefs.json` | Desktop CMP UI preferences (palette, backdrop, EQ, update snooze) |
| `~/.local/share/musico-online/musico-online/settings.json` | Persisted Rust `AppSettings` |
| `~/.local/share/musico-online/musico-online/db/` | `sled` database (recommender, playlists, likes, `mobile:*` track metadata) |
| `~/.cache/musico-online/musico-online/online/` | Downloaded stream cache + `.meta.json` sidecars |
| `~/.cache/musico-online/musico-online/covers/` | Cover-art cache |

See [data-storage.md](data-storage.md) for Windows, macOS, Flatpak, and Android paths plus the full schema.

### Settings

`musico_core/src/config.rs` defines `AppSettings`. Every field has a serde default, so old or partial JSON stays loadable. Adding a field means:

1. Add it to `AppSettings` with `#[serde(default)]` or a `#[serde(default = "…")]`.
2. Add it to `impl Default`.
3. Apply it in `musico_core/src/mobile.rs` if it affects playback/runtime state.
4. Expose it in Kotlin: `CoreSettings` (`android/shared/.../core/Models.kt`), `MusicViewModel.kt`, and `SettingsScreen.kt`.
5. Add a round-trip or partial-JSON test in `musico_core/src/config.rs`.
6. Document it in [data-storage.md](data-storage.md).

### Environment variables

| Variable | Effect |
|---|---|
| `RUST_LOG` | Raises log verbosity (for example `RUST_LOG=debug`). Logs go to `stderr` on Desktop or Logcat (`musico-native`) on Android. |
| `MUSICO_NATIVE_LIB` | Overrides the path to `libmusico_online_lib.*` / `musico_online_lib.dll` on Desktop JVM. |
| `MUSICO_VERSION_OVERRIDE` | Compile-time override for `app_display_version()` in `mobile.rs` (alongside Gradle `-PmusicoVersionCode` / `-PmusicoVersionName`). |
| `MUSICO_ONLINE_CACHE_MB` | Overrides the stream-cache cap in MB. Beats the settings slider and the 1 GB default. |
| `MUSICO_CACHE_DIR` | Overrides the stream-cache root only. Intended for tests; covers still use the normal cache dir. |
| `MUSICO_PIPED_INSTANCES` | Comma-separated Piped hosts. Empty disables Piped. |
| `MUSICO_INVIDIOUS_INSTANCES` | Comma-separated Invidious hosts. Empty disables Invidious. |
| `MUSICO_INNERTUBE_DIRECT` | Blank `""` disables `InnerTubeDirectSource` (for hermetic tests). Any unset or non-empty value keeps it enabled (`music.youtube.com`). |
| `MUSICO_SAAVN_API` | Overrides the JioSaavn API base (proxy/mirror); blank falls back to the default endpoint. |
| `MUSICO_LRCLIB_API` | Overrides the LRCLIB base URL. |
| `MUSICO_TEST_WAV` | Path to the test tone used by `musico_playback` E2E tests (default `/tmp/musico-test-tone.wav`). |

`MUSICO_LIVE_TESTS` appears in `#[ignore]` messages as a convention, but **the test code does not read it**. The `#[ignore]` attribute is what gates live tests; run them with `-- --ignored`.

## 5. Logs and debugging

```sh
# Desktop CMP (logs to stderr)
RUST_LOG=debug gradle -p android :desktopApp:run

# Android CMP
adb logcat -s musico-native Music0EQ
```

- Startup logs recommender bootstrap, stream-server prewarm, and platform/audio initialization.
- Online failures are intentionally quiet in the UI but logged at `debug!` (fast-path instance failures) or `warn!` (parse-to-zero, dispatch errors).
- For web-preview DOM/CSS work, use `npm run dev` and browser devtools.

There is no telemetry endpoint and no remote logging.

## 6. Common tasks

### Add a JNI command

For the Compose Multiplatform JNI bridge in `musico_core/src/mobile.rs`:

1. Handle the command name in `dispatch()` inside `musico_core/src/mobile.rs`:

   ```rust
   "my.command" => handle_my_command(&args),
   ```

2. Validate inputs cleanly and return `Result<serde_json::Value, String>` so errors serialize back via `MusicoCoreCodec`.
3. Call it from Kotlin with `MusicoCore.call("my.command", args)` and add tests in `musico_core` and `android/shared/src/desktopTest/`.
4. Document it in [ipc.md](ipc.md).

### Add an online source

1. Add a module under `musico_core/src/online/` and a stable source namespace (for example `"saavn"`). The namespace is part of every persisted song id (`yt:<video_id>` or `<source>:<id>`).
2. Extend `OnlineTrack` / parsing in `online/models.rs` and `online/parse.rs` only for response-shape work.
3. Dispatch in `online/stream.rs` (`resolve_track` and `resolve_track_streaming`) before the `yt-dlp` path. Unknown source strings must fail honestly rather than falling into `yt-dlp`.
4. Validate IDs, URLs, content types, and size limits at the boundary. Follow the `.part` → verify → atomic rename → `.meta.json` sidecar → prune pattern.
5. Add hermetic tests for selection, validation, and failure. Add one `#[ignore]` live test.
6. Update [online-stack.md](online-stack.md).

Do not add a source that requires a private credential to the repository. The project has no server-side secret store.

### Add a Compose Multiplatform screen or overlay

1. Add the screen/overlay composable under `android/shared/src/commonMain/kotlin/io/github/aryan007h/musicoonline/ui/` following Material 3 Expressive tokens in `theme/Theme.kt` and `components/Common.kt`.
2. Wire navigation/state in `MusicViewModel.kt` (`AppTab` / `AppOverlay`) and `MusicoApp.kt` (respecting the `840.dp` compact/expanded breakpoint and `safeCornerDp` clamping).
3. Add platform hooks via `expect`/`actual` in `Platform.kt` (`AndroidPlatform.kt` / `DesktopPlatform.kt`) only when OS-specific APIs are needed.
4. Add unit tests in `android/shared/src/desktopTest/.../SharedCoreTests.kt` and verify compilation with `gradle -p android :app:compileDebugKotlin :desktopApp:compileKotlin :shared:desktopTest`.

*(For the legacy `frontend/` web preview only: add `<section id="view-<name>" class="view">` in `frontend/index.html`, render in `core/shell.js`, bump `?v=` stamps, and update a script in `scripts/browser_*_test.mjs`.)*

### Change a resolver or cache rule

- Keep network and disk work off the UI thread.
- Every retry loop needs a total budget, not a per-attempt budget multiplied by retries.
- Every download needs a size cap, a minimum-size verify, and atomic publish.
- Every cache needs a cap and a prune policy.
- Add a test that proves the bound (fixture-based where possible).

## 7. Workspace layout

```text
musico_playback/      Audio engine (Rust, Desktop + tests)
  src/decoder.rs      Symphonia decode loop (64 KiB MediaSourceStream)
  src/http_source.rs  LoopbackHttpMediaSource (127.0.0.1 HTTP/1.1 range read/seek)
  src/output.rs       CPAL device + ring buffer
  src/resampler.rs    Rubato sample-rate conversion
  src/queue.rs        Playback queue semantics
  src/eq.rs           10-band ISO biquad EQ, stem isolation, tube warmth
  src/crossfade.rs    Crossfade curves
  src/events.rs, state.rs, error.rs
  tests/engine_e2e.rs Real-device end-to-end tests

musico_recommender/   Local recommender, no network (19 modules)
  src/models.rs       sled Store + frozen bincode types
  src/extractor.rs    30-dim acoustic feature extraction
  src/profile.rs, adapt.rs, artists.rs, clusters.rs, context.rs, diversity.rs
  src/history.rs, likes.rs, session.rs, stats.rs, vector_store.rs
  src/recommender.rs  8-signal combination + MMR ranking
  src/library.rs, user_playlists.rs, batch.rs, playlists.rs, errors.rs

android/              Compose Multiplatform app (:shared, :app, :desktopApp)

musico_core/          Pure-Rust JNI core + online stack
  src/mobile.rs       JNI initNative + invoke dispatcher (48 commands) + indexer worker
  src/config.rs       AppSettings + serde defaults
  src/identity.rs     Data/cache/config namespace ("musico-online")
  src/cover.rs        Cover path sanitization
  src/lyrics.rs       LRC parser + word-sync DTO builder
  src/online/         Catalog, resolvers, stream_server, playlist_import, lyrics

flatpak/              Flatpak manifest, desktop file, AppStream metainfo
frontend/, src/engine/ Standalone web/PWA demo (Node server.ts)
scripts/              package_appimage.sh, package_flatpak.sh, gen_test_tone.sh, browser suites
```

## 8. Platform notes

- **Linux:** standard Compose `Window` (`Main.kt`); packages as `.deb`, `.rpm`, `.tar.gz`, `.AppImage`, and `.flatpak`. Desktop MPRIS D-Bus controls are not yet implemented.
- **macOS:** Apple Silicon (`macos-arm64`) `.dmg` and `.app.tar.gz`, unsigned (`Music0.app`).
- **Windows:** standard Compose `Window`, `.msi` and portable `.zip`, unsigned.
- **Android:** `arm64-v8a` APK (`io.github.aryan007h.musicoonline`); Media3 `ExoPlayer` + `PlaybackService` + AudioFX `MobileEqualizer`; pure-Rust resolvers only (no `yt-dlp`); SAF `OpenDocument` file picker for playlist import.
- **Web/PWA preview:** mock/Express backend (`server.ts`), service worker shell cache, `MediaSession` controls.

## 9. Where to go next

- [testing.md](testing.md) — how to prove a change works.
- [ipc.md](ipc.md) — production JNI command catalog (and web-preview mock appendix).
- [online-stack.md](online-stack.md) — catalog, progressive streaming, and playlist import internals.
- [frontend.md](frontend.md) — web preview UI architecture (separate from CMP).
- [troubleshooting.md](troubleshooting.md) — when the environment fights back.
