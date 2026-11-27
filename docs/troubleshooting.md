# Troubleshooting

Start with the logs and the exact failure. Most issues fall into one of five groups: missing/wrong native core library, audio device unavailable, network source failure, cache/DB lock or state, or packaging/installer warnings.

## 1. First response

Collect this before changing anything:

```sh
# Versions across manifests
grep -n 'version' musico_core/Cargo.toml android/app/build.gradle.kts android/desktopApp/build.gradle.kts

# Checkout commit
git rev-parse --short HEAD
git status --short
```

Run with debug logging enabled:

```sh
# Desktop Compose Multiplatform (logs go to stderr; there is no musico.log file)
RUST_LOG=debug gradle -p android :desktopApp:run

# Android (via adb)
adb logcat -s musico-native Music0EQ
```

Then check:
- Does it fail on **Android** or **Desktop**?
- Is **Airplane Mode** (`offline_mode`) enabled in Settings?
- On Desktop, is `yt-dlp` (and `deno` for YouTube EJS extraction outside Flatpak) installed and current?
- Is `MUSICO_NATIVE_LIB` set or pointing to a stale build?

---

## 2. Startup, window, and installer issues

### "Core library unavailable" / missing `libmusico_online_lib` on Desktop

Compose Desktop (`MusicoCore.desktop.kt`) looks for `libmusico_online_lib.so` (Linux), `libmusico_online_lib.dylib` (macOS), or `musico_online_lib.dll` (Windows) in `MUSICO_NATIVE_LIB`, `java.library.path`, app bundle resources, and Cargo `target/{release,debug}/`. If running from source:

```sh
cargo build --release -p musico_core --lib
gradle -p android :desktopApp:run
```

Or pass `MUSICO_NATIVE_LIB=/absolute/path/to/libmusico_online_lib.so`.

### The app starts on Desktop but audio fails

`musico_core` logs `"desktop audio init failed: ..."` or `"desktop audio engine unavailable"` when CPAL cannot open an output device. Check that PipeWire/PulseAudio/ALSA is running and your user session has access:

```sh
pactl info
aplay -l
```

A headless SSH session or container without an audio socket reproduces this; it is environmental, not a decoder bug.

### Second Desktop launch hits a sled DB lock error

Compose Desktop does **not** currently have a single-instance window lock. Launching a second instance concurrently causes sled to fail opening `db/` (`"recommender open failed: ..."` / `"mobile core: recommender bootstrap deferred"`), leaving the second window without library, likes, or playlists. Close all running instances (`pgrep -af 'Music0|MainKt'`) and relaunch once.

### AppImage or Flatpak issues on Linux

- **AppImage without FUSE:** run `./music0-linux-x86_64.AppImage --appimage-extract-and-run` (or install `libfuse2`).
- **Local `flatpak-builder` fails on Ubuntu 24.04+:** AppArmor blocks unprivileged user namespaces needed by bubblewrap. Allow it temporarily:
  ```sh
  sudo sysctl -w kernel.apparmor_restrict_unprivileged_userns=0
  ```
- **Wayland vs. X11:** The Flatpak manifest (`io.github.aryan007h.Music0.yml`) uses `--socket=x11` (XWayland on Wayland sessions).

### macOS says the app is damaged or from an unidentified developer

Release builds are unsigned. Use right-click → **Open**, or remove the quarantine attribute on the actual bundle name (`/Applications/Music0.app`):

```sh
xattr -cr "/Applications/Music0.app"
```

### Windows SmartScreen blocks the installer

Release `.msi` and `.zip` builds are unsigned. Choose **More info → Run anyway** after verifying `SHA256SUMS.txt` ([verify-downloads.md](verify-downloads.md)).

### Android Play Protect warns on APK install or update

Sideloaded FOSS APKs and self-updating installers can trigger Google Play Protect (`"Harmful app blocked"`), even when VirusTotal reports `0/66` and the APK is signed by the same release key. Tap **Install anyway** and verify the APK SHA-256 and `apksigner` fingerprint using [verify-downloads.md](verify-downloads.md).

### Android `UpdateManager` fails to download or install update

- Ensure **"Install unknown apps"** permission is granted to Music0 in Android Settings.
- `UpdateManager.kt` rejects downloads whose SHA-256 digest does not match GitHub's `assetDigest` or that exceed its size ceiling; retry on a stable connection.

---

## 3. Playback problems

### `previous` restarts the track instead of going back

By design: if the current position is greater than 3 seconds, `previous` seeks to `0`. Press it again within 3 seconds to jump to the previous track.

### The queue skips tracks

In CMP (`ResolveFailurePolicy` in `MusicViewModel.kt`), background/autoplay track resolution stops auto-skipping after **`3`** consecutive failures so a network outage cannot burn through your entire queue. User-initiated plays show a **Retry** toast instead of skipping.

### Desktop progressive playback stalls or falls back

Interactive playback (`allowStream = true`) opens `http://127.0.0.1:<port>/stream/<token>` via `LoopbackHttpMediaSource` (`musico_playback/src/http_source.rs`) once `16 KiB` lands (`4 s` `INITIAL_READY_TIMEOUT`). If a slow upstream stalls mid-chunk beyond `30 s` (`MAX_STALL_WAIT`), playback reports a timeout error and falls back cleanly.

### EQ changes on Android or Desktop seem subtle or inactive

- Check `adb logcat -s Music0EQ` on Android to confirm `AudioFX` `hasControl()` is true (another system EQ app can steal session control).
- On Desktop, check stderr for `mobile core: desktop EQ enabled=...`.

---

## 4. Online, search, lyrics, and import problems

### "Offline — turn Airplane Mode off to reach YouTube Music"

Airplane Mode (`offline_mode`) is persisted in `settings.json`. Toggle it off in **Settings**. Cached tracks in **Offline Downloads** still play while offline.

### "Direct-URL sources (Piped/Invidious) failed" / missing `yt-dlp`

- **Android** cannot run `yt-dlp` at all and relies solely on JioSaavn, `InnerTubeDirectSource`, Piped, and Invidious.
- **Linux Flatpak** bundles `yt-dlp`, `yt-dlp-ejs`, and `deno` inside `/app/bin`.
- **Other Desktop installs:** install both `yt-dlp` and `deno` (needed by `yt-dlp` for YouTube EJS challenge solving):
  ```sh
  # Debian/Ubuntu
  sudo apt install yt-dlp
  # Arch
  sudo pacman -S yt-dlp deno
  # macOS
  brew install yt-dlp deno
  # Windows
  winget install yt-dlp.yt-dlp
  ```
  `yt-dlp` availability is cached for 10 minutes; restart the app after installing.

### Every online play hangs for ~6–10 seconds, then fails

The direct-URL fast path (`InnerTubeDirectSource`, Piped, Invidious) exhausted its budget (`STREAM_FAST_PATH_BUDGET = 6 s` for progressive streaming, `FAST_PATH_BUDGET = 10 s` for whole-file download) and `yt-dlp` also failed (or isn't present on Android). Run with `RUST_LOG=debug` to see per-source failures, or override instance pools:

```sh
MUSICO_PIPED_INSTANCES=pipedapi.example.com \
MUSICO_INVIDIOUS_INSTANCES=inv.example.com \
gradle -p android :desktopApp:run
```

Setting `MUSICO_INNERTUBE_DIRECT=""` (blank) disables `InnerTubeDirectSource`; any non-empty value keeps it enabled (`music.youtube.com`).

### A YouTube track resolves to the wrong version via JioSaavn

JioSaavn cross-source matching (`musico_core/src/online/saavn.rs`) searches 6 candidates (`2.5 s` cap), checks title/artist overlap, allows `<= 6 s` or `<= 20%` relative duration drift, and picks the closest duration. Report the YouTube `videoId` and mismatched JioSaavn match if a bad match slips through.

### Search returns nothing even though network is up

- A parsed-zero page usually means YouTube Music changed InnerTube's JSON shape (`parse.rs`). Confirm with:
  ```sh
  cargo test -p musico_core live_search_and_radio -- --ignored --nocapture
  ```
- If JioSaavn rows are missing while YouTube results work, JioSaavn may be region-blocked, its `6 s` API timeout expired, or `MUSICO_SAAVN_API` is pointing to a broken mirror.

### Lyrics are missing or out of sync

`lyrics.enhanced` checks **LRCLIB** (exact/search match with `±2 s` duration window when duration is known) → **YouTube Music** plain lyrics.
- LRCLIB may lack synced lines for niche/new releases; `synced: false` or empty lines is a normal miss.
- Word-level timing is synthesized from line timestamps (`musico_core/src/lyrics.rs`), so intra-line highlighting can drift slightly.

### Playlist URL import fails

- Only `https://open.spotify.com/...` and `https://music.apple.com/...` public playlist URLs are allowlisted (private playlists cannot be fetched without OAuth).
- File imports (`.csv`, `.json`, `.m3u`, `.m3u8`) are capped at `4 MiB` and `500` tracks.

### Cache usage / clearing doesn't free `db/` or covers

`online.cache.usage` and `online.cache.clear` operate only on `<cache_dir>/online/` (including `.part`, `.ytdl`, `.tmp`, and `.meta.json` sidecars; stale partials are swept after `24 h`). Covers (`covers/`), `sled` DB (`db/`), `settings.json`, and `ui_prefs.json` are separate — see [data-storage.md](data-storage.md).

---

## 5. Test and CI problems

### Live tests fail with network/timeout errors

```sh
MUSICO_CACHE_DIR=/tmp/musico-live-cache \
  cargo test -p musico_core live_ -- --ignored --nocapture
```

`MUSICO_LIVE_TESTS=1` is not read by Rust; `--ignored` is the gate.

### Playback tests fail in CI or headless shells

`cargo test --workspace` runs `musico_playback/tests/engine_e2e.rs`, which needs an audio device and `/tmp/musico-test-tone.wav` (`bash scripts/gen_test_tone.sh`). CI runs `cargo test --workspace --exclude musico_playback`; run `cargo test -p musico_playback --lib` for hermetic loopback/DSP unit tests without audio hardware.

### CI passes on Linux but release fails on macOS/Windows

The release workflow compiles platform-specific `cfg` blocks and packages `.dmg`/`.msi`. Trigger `workflow_dispatch` on `.github/workflows/release.yml` to verify before tagging (see [release.md §4](release.md#4-verify-without-tagging)).

---

## 6. Data and settings problems

### Settings are ignored or reset

A corrupt `settings.json` falls back to `AppSettings::default()` and logs `"Corrupt settings file, using defaults"`. Check the actual nested path in [data-storage.md](data-storage.md#1-directory-layout-and-path-resolution) (on Linux Desktop: `~/.local/share/musico-online/musico-online/settings.json`), close the app, and move/delete the broken file.

### "Recommender unavailable" or sled lock error

Close all Music0 instances (`pgrep -af 'Music0|MainKt'`) so the `sled` lock is released. If the error persists, back up and inspect/remove `db/` (note: removing `db/` resets likes, playlists, and play history).

### The DB grows over time

Play events in `sled`'s `events` tree do not yet have automatic compaction/retention. If `db/` grows undesirably large, back up and reset per [data-storage.md §6](data-storage.md#6-resetting-local-state).

---

## 7. Release and packaging

- Tags must start with `v` (`v*`). Tags containing `-` (like `-rc.1`) mark the GitHub Release as a prerelease (`v1.0.1` has no hyphen and publishes as Latest).
- `publish` runs only on tag pushes (`refs/tags/*`), never on `workflow_dispatch`, and requires all 4 platform jobs (`linux-desktop`, `macos-desktop`, `windows-desktop`, `android-native`, each using `if-no-files-found: error`) to pass.
- Remember to keep all synced version files aligned ([release.md §1](release.md#1-versioning)).

---

## 8. Where to go next

- [development.md](development.md#5-logs-and-debugging) — logging and local runs.
- [online-stack.md](online-stack.md) — resolver, loopback streaming, and timeout details.
- [data-storage.md](data-storage.md) — paths, schema, and reset.
- [testing.md](testing.md) — test layers and hermetic rules.
- [SECURITY.md](../SECURITY.md) — reporting a vulnerability instead of a bug.
