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
| Node.js | 20+ recommended | Web preview, TypeScript engine, browser suites |
| Tauri CLI | 2.x | `cargo tauri dev`, `cargo tauri build` |
| `yt-dlp` | current | Last-resort stream resolver |
| `playwright` + Chrome | current | Browser suites only (not a runtime dependency) |

### Linux system packages

The release workflow installs this set; `ci.yml` installs the subset needed to
compile and test (without the bundling tools):

```sh
sudo apt-get update
sudo apt-get install -y \
  libasound2-dev pkg-config libfontconfig1-dev \
  libwebkit2gtk-4.1-dev libgtk-3-dev libayatana-appindicator3-dev \
  librsvg2-dev patchelf libwayland-dev libxkbcommon-dev
```

`libasound2-dev` is required by CPAL; the WebKitGTK/GTK packages are required
by Tauri. A running PipeWire/PulseAudio server is needed to *run* the app, but
not to compile it.

### macOS

Install Xcode Command Line Tools (`xcode-select --install`). For a universal
build, add both Rust targets:

```sh
rustup target add aarch64-apple-darwin x86_64-apple-darwin
```

### Windows

Install the MSVC build tools, Rust, and WebView2 (present by default on
Windows 11). The release workflow builds with the
`x86_64-pc-windows-msvc` target.

### Android (optional)

Android builds need the Android SDK, NDK, and a JDK, configured for Tauri 2:

```sh
cargo tauri android init
cargo tauri android build --apk
```

The app crate exposes a `cdylib` for the Android JNI entry point. Desktop-only
dependencies (`rfd`, `tauri-plugin-single-instance`) are `cfg`-gated out on
Android. Android uses the pure-Rust resolvers (Piped, Invidious, InnerTube
Direct, JioSaavn) because it cannot spawn `yt-dlp`. Android is not built by CI;
expect to debug the local toolchain.

## 2. First build

```sh
cargo build -p musico-online   # app crate only (fastest useful check)
cargo build --workspace        # all three crates
npm install                    # web preview + TypeScript tests
cargo install tauri-cli --version '^2'
```

If `cargo` cannot find a linker or `pkg-config`, install the system packages
above before retrying. Do not add vendored binaries to the repository.

## 3. Running the app

### Desktop (Tauri)

The repository has **no frontend bundler**. `src-tauri/tauri.conf.json` sets
`devUrl` to `http://localhost:1420` and leaves `beforeDevCommand` empty, so
serve the static frontend yourself:

```sh
# terminal 1
python3 -m http.server 1420 --directory frontend

# terminal 2
cargo tauri dev
```

The webview loads `frontend/index.html` directly. There is no compile step for
HTML, CSS, or the classic JS; editing those files and reloading the webview is
enough for most UI work a static server can serve. Rust changes trigger a
rebuild in the Tauri dev process.

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

```sh
cargo tauri build
```

Artifacts land under `target/release/bundle/`:

- Linux: `deb/`, `rpm/`, `appimage/`
- macOS: `dmg/`, `macos/`
- Windows: `msi/`, `nsis/`

The release workflow uses `tauri build --bundles deb,rpm,appimage`,
`--target universal-apple-darwin --bundles dmg,app`, and `--bundles msi,nsis`.
See [release.md](release.md).

### Useful flags

- `cargo run -p musico-online -- --scan-on-start` forces a library scan even
  when a music folder was already configured.
- Starting a second instance focuses the existing window instead of opening a
  second DB/audio engine (desktop only).

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

All paths derive from `src-tauri/src/identity.rs`. The `musico-online`
namespace is deliberate so the app can run next to the offline Musico install.
See [data-storage.md](data-storage.md) for the full schema.

### Settings

`src-tauri/src/config.rs` defines `AppSettings`. Every field has a serde
default, so old or partial JSON stays loadable. Adding a field means:

1. Add it to `AppSettings` with `#[serde(default)]` or a `#[serde(default = "…")]`.
2. Add it to `impl Default`.
3. Apply it in `commands::apply_settings_to_engine` if it affects audio.
4. Expose it in `frontend/index.html` + `frontend/js/app.js` if it is a UI
   setting.
5. Add a round-trip or partial-JSON test in `config.rs`.
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
RUST_LOG=debug cargo tauri dev
```

- Startup logs the recommender path, single-instance behavior, batch resume,
  audio-engine initialization, and platform media-control setup.
- Online failures are intentionally quiet in the UI but logged at `debug!`
  (fast-path instance failures) or `warn!` (parse-to-zero, batch failure).
- The event bridge polls playback events every 50 ms; `playback-event` is the
  single stream the frontend uses for transport truth.
- For DOM/CSS work, use the web preview and browser devtools. In a debug Tauri
  build, use the webview inspector where the platform exposes it.

There is no telemetry endpoint and no remote logging. A bug report must include
a manually copied log excerpt.

## 6. Common tasks

### Add an IPC command

1. Implement the handler in `src-tauri/src/commands.rs`:

   ```rust
   #[tauri::command]
   pub fn my_command(state: State<AppState>, song_id: String) -> Result<String, String> {
       // validate → dispatch → return a serializable value
       Ok(song_id)
   }
   ```

   Use `async fn` for anything that can block; bridge to blocking work with
   `tauri::async_runtime::spawn_blocking`. Return `Result<T, String>` so the
   webview sees a rejected promise on failure.

2. Register it in `src-tauri/src/lib.rs` inside `tauri::generate_handler!`.
   Registering is mandatory — an unregistered command fails at runtime.

3. Call it from JS with **camelCase** arguments (Tauri maps `song_id` to
   `songId`):

   ```js
   const value = await Bridge.invoke('my_command', { songId: 'yt:abc' });
   ```

4. Add the command to [ipc.md](ipc.md), a test if the behavior is non-trivial,
   and a `CHANGELOG.md` entry if it is user-visible.

### Add an online source

1. Add a module under `src-tauri/src/online/` and a stable source namespace
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
6. Update `get_platform_capabilities` if the source is platform-dependent.
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

src-tauri/            Tauri app
  src/commands.rs     IPC handlers (append-only hotspot)
  src/state.rs        AppState
  src/event_bridge.rs Playback poller + advance policy
  src/scanner.rs      Library scan + metadata/cover cache
  src/online/         Catalog + stream stack
  src/mpris.rs        Linux MPRIS
  src/media_controls.rs macOS/Windows media controls

frontend/             No-build UI
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
