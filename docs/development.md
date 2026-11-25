# Development

This guide gets a working development environment and explains the tasks you
will do most often. It assumes the repository root as the working directory.
For the workflow (branches, PRs, review), read
[CONTRIBUTING.md](../CONTRIBUTING.md). For the system design, read
[ARCHITECTURE.md](../ARCHITECTURE.md).

## 1. Prerequisites

| Tool | Version | Why |
|---|---|---|
| Rust | stable, MSRV 1.78 | Workspace crates |
| Cargo | bundled with Rust | Builds and tests |
| Java & Gradle | JDK 17+, Gradle 8.10.2 | Compose Multiplatform Android & Desktop |
| Node.js | 20+ recommended | Web preview, TypeScript engine, browser suites |
| `yt-dlp` | current | Last-resort stream resolver on Desktop |
| `playwright` + Chrome | current | Browser suites only (not a runtime dependency) |

### Linux system packages

CI installs this minimal set (no WebKitGTK/GTK packages required):

```sh
sudo apt-get update
sudo apt-get install -y libasound2-dev pkg-config
```

`libasound2-dev` is required by CPAL. A running PipeWire/PulseAudio server is
needed to *run* desktop audio playback, but not to compile it.

### macOS

Install Xcode Command Line Tools (`xcode-select --install`) and Java 17+.

### Windows

Install the MSVC build tools, Rust, and Java 17+. The release workflow builds
with the `x86_64-pc-windows-msvc` target.

### Android

Cross-compile `musico_core` for `arm64-v8a` using `cargo-ndk` (with Android
SDK/NDK configured), then assemble the debug APK:

```sh
(cd musico_core && cargo ndk -t arm64-v8a -o ../android/app/src/main/jniLibs build --release --lib)
gradle -p android :app:assembleDebug
```

Android uses the pure-Rust resolvers (Piped, Invidious, InnerTube Direct,
JioSaavn) because it cannot spawn `yt-dlp`.

## 2. First build

```sh
cargo build --release -p musico_core --lib   # JNI core cdylib
cargo test --workspace --exclude musico_playback
gradle -p android :desktopApp:run            # Compose Desktop
npm install                                  # web preview + TypeScript tests
```

If `cargo` cannot find a linker or `pkg-config`, install the system packages
above before retrying. Do not add vendored binaries to the repository.

## 3. Running the app

### Desktop (Compose Multiplatform)

```sh
cargo build --release -p musico_core --lib
gradle -p android :desktopApp:run
```

The Desktop JVM app auto-discovers `target/release/libmusico_online_lib.*` (or
accepts `MUSICO_NATIVE_LIB=/path/to/lib`).

### Web / PWA preview

```sh
npm run dev              # http://localhost:3000
PORT=8143 npm run dev    # browser-suite port
```

`server.ts` serves `frontend/` and implements a demo RPC backend with a seeded
catalog and synthesized audio. It is useful for UI and PWA work, not for
verifying real stream resolution. It binds `0.0.0.0`, so never expose it to an
untrusted network.

### Production bundle

See [release.md](release.md) and the **Packaging Desktop installers** section
of [README.md](../README.md#packaging-desktop-installers-compose-multiplatform)
for `.deb`, `.rpm`, `.dmg`, `.msi`, and `.apk` packaging commands.

## 4. Configuration

### Files

| Path | Purpose |
|---|---|
| `~/.config/musico-online/settings.json` | Persisted `AppSettings` |
| `~/.local/share/musico-online/db/` | sled database (library index, taste, playlists, batches) |
| `~/.local/share/musico-online/musico.log` | App log |
| `~/.cache/musico-online/online/` | Downloaded stream cache |
| `~/.cache/musico-online/covers/` | Cover-art cache |
| `~/.cache/musico-online/library.json` | Local scan metadata cache |

All paths derive from `musico_core/src/identity.rs`. The `musico-online`
namespace is deliberate so the app can run next to the offline Musico install.
See [data-storage.md](data-storage.md) for the full schema.

### Settings

`musico_core/src/config.rs` defines `AppSettings`. Every field has a serde
default, so old or partial JSON stays loadable. Adding a field means:

1. Add it to `AppSettings` with `#[serde(default)]` or a `#[serde(default = "…")]`.
2. Add it to `impl Default`.
3. Apply it in `musico_core/src/mobile.rs` if it affects playback/runtime state.
4. Expose it in the Settings UI (`android/shared/.../settings/SettingsScreen.kt`).
5. Add a round-trip or partial-JSON test in `musico_core/src/config.rs`.
6. Document it in [data-storage.md](data-storage.md).

### Environment variables

| Variable | Effect |
|---|---|
| `RUST_LOG` | Raises log verbosity (for example `RUST_LOG=debug`). Logs still go to `musico.log`. |
| `MUSICO_ONLINE_CACHE_MB` | Overrides the stream-cache cap in MB. Beats the settings slider and the 1 GB default. |
| `MUSICO_CACHE_DIR` | Overrides the stream-cache root only. Intended for tests; the covers and `library.json` caches still use the normal cache dir. |
| `MUSICO_PIPED_INSTANCES` | Comma-separated Piped hosts. Empty disables Piped. |
| `MUSICO_INVIDIOUS_INSTANCES` | Comma-separated Invidious hosts. Empty disables Invidious. |
| `MUSICO_INNERTUBE_DIRECT` | Comma-separated direct InnerTube hosts (default `www.youtube.com`). |
| `MUSICO_SAAVN_API` | Overrides the JioSaavn API base (proxy/mirror). |
| `MUSICO_LRCLIB_API` | Overrides the LRCLIB base URL. |
| `MUSICO_TEST_WAV` | Path to the test tone used by playback/library tests (default `/tmp/musico-test-tone.wav`). |

`MUSICO_LIVE_TESTS` appears in `#[ignore]` messages and documentation as a
convention, but **the test code does not read it**. The `#[ignore]` attribute
is what gates live tests; run them with `-- --ignored`.

## 5. Logs and debugging

```sh
tail -f ~/.local/share/musico-online/musico.log
RUST_LOG=debug gradle -p android :desktopApp:run
```

- Startup logs recommender bootstrap, stream-server prewarm, and platform/audio initialization.
- Online failures are intentionally quiet in the UI but logged at `debug!`
  (fast-path instance failures) or `warn!` (parse-to-zero, batch failure).
- For web-preview DOM/CSS work, use `npm run dev` and browser devtools.

There is no telemetry endpoint and no remote logging. A bug report must include
a manually copied log excerpt.

## 6. Common tasks

### Add an IPC command

For the Compose Multiplatform JNI bridge in `musico_core/src/mobile.rs`:

1. Handle the command name in `dispatch()` inside `musico_core/src/mobile.rs`:

   ```rust
   "my.command" => handle_my_command(&args),
   ```

2. Validate inputs cleanly and return `Result<serde_json::Value, String>` so
   errors serialize back via `MusicoCoreCodec`.
3. Call it from Kotlin with `MusicoCore.call("my.command", args)` and add tests
   in `musico_core` and `android/shared/src/desktopTest/`.
4. For the web-preview bridge reference, see [ipc.md](ipc.md).

### Add an online source

1. Add a module under `musico_core/src/online/` and a stable source namespace
   (for example `"saavn"`). The namespace is part of every persisted song id:
   `yt:<video_id>` or `<source>:<id>`.
2. Extend `OnlineTrack` / parsing in `online/models.rs` and
   `online/parse.rs` only for response-shape work.
3. Dispatch in `online/stream.rs::resolve_track` before the yt-dlp path. Unknown
   source strings must fail honestly rather than falling into yt-dlp.
4. Validate ids, URLs, content types, and size limits at the boundary. Follow
   the existing `.part` → verify → rename → prune pattern.
5. Add hermetic tests for selection, validation, and failure. Add one
   `#[ignore]` live test.
6. Update `dispatch()` capability metadata if the source is platform-dependent.
7. Update [online-stack.md](online-stack.md) and the source/legal note.

Do not add a source that requires a private credential to the repository. The
project has no server-side secret store.

### Add a frontend view

1. Add a `<section id="view-<name>" class="view">` in `frontend/index.html`.
2. Render it from `frontend/js/core/shell.js` (for Preact/htm views) or from
   `app.js` (for the legacy controller path).
3. Route it through `MusicoRouter.navigate('<name>')`; add a sidebar entry if it
   is a top-level destination.
4. Cache data in `MusicoStore` and subscribe rather than refetching on every
   render.
5. Bump the `?v=` stamps in `frontend/index.html` (and module imports that
   carry their own stamp).
6. Add or extend a browser suite, then run it. See [frontend.md](frontend.md)
   and [testing.md](testing.md).

### Change a resolver or cache rule

- Keep network and disk work off the UI thread.
- Every retry loop needs a total budget, not a per-attempt budget multiplied
  by retries.
- Every download needs a size cap, a minimum-size verify, and atomic publish.
- Every cache needs a cap and a prune policy.
- Add a test that proves the bound (fixture-based where possible).

## 7. Workspace layout

```text
musico_playback/      Audio engine, no Tauri dependency
  src/decoder.rs      Symphonia decode loop
  src/output.rs       CPAL device + ring buffer
  src/queue.rs        Playback queue semantics
  src/eq.rs           EQ / DSP
  src/crossfade.rs    Crossfade curves
  tests/engine_e2e.rs Real-device end-to-end tests

musico_recommender/   Local recommender, no network
  src/models.rs       sled Store + frozen bincode types
  src/extractor.rs    Audio feature extraction
  src/profile.rs      Long-term taste profile
  src/recommender.rs  Signal combination + ranking
  src/batch.rs        Persistent batch-download records
  src/library.rs      Saved albums/artists/tracks
  src/user_playlists.rs

android/              Compose Multiplatform app (:shared, :app, :desktopApp)

musico_core/          Pure-Rust JNI core (`mobile.rs`) + online stack
  src/mobile.rs       JNI command dispatcher (`dispatch`)
  src/config.rs       AppSettings + migrations
  src/identity.rs     Data/cache/config namespace
  src/lyrics.rs       LRC parser
  src/online/         Catalog, resolvers, stream server, batch downloads

frontend/             Web/PWA preview UI
src/engine/           TypeScript engine for server.ts
scripts/              Browser suites + contrast gate
```

## 8. Platform notes

- **Linux:** system title bar; MPRIS via D-Bus (`org.mpris.MediaPlayer2.musico-online`).
- **macOS:** overlay traffic lights, native App/Edit/View/Window menus, SF Pro
  stack, universal release builds, unsigned.
- **Windows:** native decorations removed at runtime; drawn min/max/close;
  the native title bar can flash briefly during launch. Unsigned installers.
- **Android:** no `yt-dlp`; pure-Rust resolvers only; no native folder picker.
- **Web/PWA:** mock backend, service worker shell cache, MediaSession controls.

## 9. Where to go next

- [testing.md](testing.md) — how to prove a change works.
- [frontend.md](frontend.md) — UI architecture and cache busting.
- [ipc.md](ipc.md) — command/event contracts.
- [online-stack.md](online-stack.md) — catalog and stream internals.
- [troubleshooting.md](troubleshooting.md) — when the environment fights back.
