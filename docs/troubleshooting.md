# Troubleshooting

Start with the log and the exact failure. Most issues fall into one of five
groups: the frontend was not served, the audio device is unavailable, a network
source is down, the cache/DB is in a bad state, or a build is stale.

## 1. First response

Collect this before changing anything:

```sh
# Version
grep '^version' src-tauri/Cargo.toml src-tauri/tauri.conf.json package.json

# Log (Linux)
tail -n 200 ~/.local/share/musico-online/musico.log

# Was the app built from this checkout?
git rev-parse --short HEAD
git status --short
```

Then answer:

- Does it fail in the Tauri app, the web preview, or both?
- Does it fail for local files, online tracks, or both?
- Is Airplane Mode on?
- Is `yt-dlp` installed and current?
- What does `get_platform_capabilities` return in the webview console?

```js
await Bridge.invoke('get_platform_capabilities')
await Bridge.invoke('online_available')
await Bridge.invoke('online_cache_usage')
```

For a reproducible bug, run with debug logging:

```sh
RUST_LOG=debug cargo tauri dev
```

`RUST_LOG` replaces the default `Info` filter and still writes to the log file.
Without it, fast-path instance failures are at `debug!` and will not appear.

## 2. The window is blank or the app will not start

### Blank window in development

Tauri loads `http://localhost:1420` and `beforeDevCommand` is empty. Start the
static server yourself:

```sh
python3 -m http.server 1420 --directory frontend
cargo tauri dev
```

If port 1420 is occupied, the webview shows a connection error, not a Rust
panic. Check the terminal running the static server and the webview devtools.

### The app starts but there is no sound and no transport

The audio engine failed to initialize. The log contains:

```text
Playback engine failed to initialize — no audio device?
```

Playback commands then reject with `Playback engine not initialized`. Check
that PipeWire/PulseAudio/ALSA is running and that the user session has access to
the device:

```sh
pactl info
aplay -l
```

A headless SSH session or a container without an audio socket will reproduce
this. It is environmental, not a decoder bug.

### A second launch does nothing

Desktop builds use `tauri-plugin-single-instance`: the second process focuses
the existing window instead of opening a second DB/engine. This is expected.
If the first instance is hung, kill it before relaunching.

### AppImage will not start on Linux

If FUSE is unavailable:

```sh
./Musico*.AppImage --appimage-extract-and-run
```

or install `libfuse2` for your distribution. The release workflow sets
`APPIMAGE_EXTRACT_AND_RUN=1` for the same reason.

### macOS says the app is damaged or from an unidentified developer

Release builds are unsigned. Use right-click → **Open**, or:

```sh
xattr -cr "/Applications/Musico Online.app"
```

Do not disable Gatekeeper globally.

### Windows SmartScreen blocks the installer

Release builds are unsigned. Choose **More info → Run anyway**, or verify the
artifact against `SHA256SUMS.txt` from the release.

### The native title bar flashes on Windows

The window is created decorated and decorations are removed at runtime so the
webview can draw min/max/close. A brief flash is expected. It is cosmetic.

## 3. Local library problems

### A scan finds nothing

- Confirm the folder actually contains supported extensions: `mp3`, `flac`,
  `ogg`, `wav`, `m4a`, `aac`, `opus`, `alac`, `wma`, `mp4`, `m4b`.
- Confirm the configured folder is the parent you expect. Scans are recursive.
- Check the log for `Scan complete: N songs`.
- On Android there is no native folder picker; `pick_folder` falls back to
  `/storage/emulated/0/Music` (or the platform audio dir).

### A file is missing after I edited its tags

`library.json` caches metadata by `file_path` + mtime + size. If a tool changes
tags without changing mtime/size, the old metadata is reused. Delete
`~/.cache/musico-online/library.json` and rescan, or touch the file.

### Covers are missing or stale

- Local covers are extracted during the scan and downscaled into
  `~/.cache/musico-online/covers/`.
- Online covers are only fetched from allowlisted HTTPS art hosts. A rejected
  URL logs at `debug!`.
- Delete the specific `{sanitized-id}.jpg` (or the whole `covers/` directory)
  and restart. There is no automatic cover-cache pruning in v0.7.0.

### Indexing takes a long time

Feature extraction is intentionally slower than the metadata scan: it decodes
audio to build the recommender vector. Progress is reported by
`index-progress`; the UI remains usable. A very large library on a slow disk can
take minutes. Do not start a second scan; `start_scan` returns `false` while one
is running.

### `set_song_like` says "Recommender unavailable"

The sled DB failed to open. Check the log for `Recommender unavailable: ...`.
Common causes: the DB directory is read-only, the disk is full, or another
process holds the sled lock. Close every instance, verify permissions on
`~/.local/share/musico-online/db/`, and relaunch.

## 4. Playback problems

### `previous` restarts instead of going back

By design: if the current position is greater than 3 seconds,
`previous` seeks to 0. Press it again within 3 seconds to pop history.

### The queue skips tracks

The advance policy skips queued entries whose file no longer exists, up to 5 in
a row, then falls through to autoplay/shuffle/stop. This is deliberate so a
cache-evicted stream does not kill the session. Re-download or remove the dead
queue row.

### Gapless/crossfade/EQ settings do nothing

Settings only take effect after `save_settings` (or the specific setter) applies
them to the engine. If the engine is `None`, setters return an error or are
no-ops. Confirm the log shows the engine initialized, then reapply the setting.

### Media keys do not work

| Platform | Mechanism | Check |
|---|---|---|
| Linux | MPRIS over D-Bus, `org.mpris.MediaPlayer2.musico-online` | `playerctl -l`, then `playerctl status` |
| Windows | SMTC | Windows media overlay |
| macOS | Now Playing | Control Center |

On Linux, a missing session bus or a sandboxed environment blocks MPRIS. The log
shows `MPRIS unavailable: ...`; playback still works.

### `SongEnded` never fires

The engine emits it after the tail is played out. A track that is seeked to the
end, a decoder error, or a device underrun can produce `Error` instead. Check
the `playback-event` stream in the webview console and the log around the
track's end.

## 5. Online problems

### "Offline — turn Airplane Mode off to reach YouTube Music"

Airplane Mode is persisted in `settings.json`. Turn it off in Settings, or:

```js
await Bridge.invoke('set_offline', { enabled: false })
```

`online_start_radio` checks Airplane Mode before it clears the upcoming queue,
so an offline attempt leaves the queue untouched.

### "yt-dlp is not installed and the direct-URL sources failed"

The fast path had no usable instance, and no yt-dlp binary was found. Install
it:

```sh
# Debian/Ubuntu
sudo apt install yt-dlp
# Arch
sudo pacman -S yt-dlp
# macOS
brew install yt-dlp
# Windows
winget install yt-dlp
```

`yt-dlp` availability is cached for 10 minutes. Installing it mid-session does
not take effect until the TTL expires or the app restarts.

`online_available` returns `false` when `yt-dlp` is missing **even if the
direct sources would work**. It is a hint for the UI, not a hard capability
gate.

### Every online play hangs for ~10 seconds, then fails

The Piped/Invidious/InnerTube Direct fast path exhausted its 10-second budget
and yt-dlp also failed. Check the log at `debug!` for per-instance reasons. Try
a different pool:

```sh
MUSICO_PIPED_INSTANCES=https://pipedapi.example.com \
MUSICO_INVIDIOUS_INSTANCES=https://inv.example.com \
cargo tauri dev
```

Hosts are comma-separated. A blank value disables that kind. The public
instance ecosystem churns; the compiled defaults are not guaranteed alive.

### "resolve budget exhausted waiting for a free downloader"

Three yt-dlp children are already running (interactive, prefetch, radio warmup,
or batch). Interactive play fails honestly instead of pinning "resolving".
Wait for the current work to finish, cancel a batch, or raise the budget in
code only as a deliberate change. Do not remove the global child cap.

### A track resolves to the wrong version

For a YouTube row, the cross-source fast path may match a different JioSaavn
recording. The heuristic accepts the first Saavn result where one title
contains the other, an artist token matches, and durations are within 4
seconds. **There is no per-track override or setting to disable this in
v0.7.0.** Report the YouTube id and the Saavn id; the heuristic is the fix
point.

### Search returns nothing but the network is up

- A parsed-zero page usually means InnerTube changed shape. The log says
  `InnerTube search parsed 0 results ... — API shape may have changed`.
- Try the live gate to confirm:
  `cargo test -p musico-online live_search_and_radio -- --ignored --nocapture`.
- A single dead instance does not empty search; it has its own retry and
  coalescing policy.

### JioSaavn rows are missing

Saavn search failures are contained and return an empty block; YouTube results
still render. Causes: the native API is region-blocked, the response shape
changed, the 3-second metadata timeout expired, or a mirror set via
`MUSICO_SAAVN_API` is down. Try without the override.

### Lyrics are missing or out of sync

The ladder is: local `.lrc` sidecar → LRCLIB exact → LRCLIB search (±2 s) →
YouTube Music unsynced. Diagnose in that order:

- For a local file, confirm `<audio-basename>.lrc` sits next to it.
- LRCLIB may not have the track; `None` is a valid result, not an error.
- An exact LRCLIB match marked instrumental returns `None` by design.
- YouTube Music does not expose timed lines; only LRCLIB provides them.
- Word-level highlighting is synthesized, so it can drift within a line.

### The download button seems stuck after a restart

`online_download` keeps a **session-scoped** state map. After a restart the map
is empty; the filesystem is the truth. Check `online_cache_usage` and whether
the file exists in `~/.cache/musico-online/online/`. Batch jobs are different:
they persist in sled and resume at startup (unless Airplane Mode is on).

### A batch stops partway

- `online_batches` shows per-track state (`pending`, `done`,
  `failed:<reason>`).
- `online_batch_retry` resets failed rows and restarts.
- `online_batch_cancel` is cooperative: in-flight tracks finish.
- At most 2 resolves run per batch, and the global yt-dlp cap is 3.
- A batch that is `active: false` with pending rows resumes on the next startup
  or when Airplane Mode is lifted.

### Cache usage is larger than the cap

`cache_usage_bytes` includes `.part` and `.ytdl` sidecars, which do not count
toward LRU eviction but do count as disk usage. Prune sweeps sidecars older
than 24 h. `MUSICO_ONLINE_CACHE_MB` overrides the settings slider; the UI
reports the effective cap, not the slider value.

### Clearing the cache did not free the space I expected

`online_clear_cache` deletes only the stream cache. Covers, `library.json`, and
the sled DB are separate. Check the paths in
[data-storage.md](data-storage.md#1-storage-map).

## 6. Frontend and browser-suite problems

### `Bridge.invoke` returns `null` for everything

Two different causes look identical:

- The command rejected; the bridge logged the error to the console.
- The command succeeded but returns `Result<(), String>` (`Ok(())` serializes
  as `null`).

Check the console for the rejection. If the command is expected to report
success, change it to return `bool` or a value.

### Events never arrive

- The capability file must grant `core:event`; otherwise `listen` rejects and
  the bridge logs loudly.
- `Bridge.listen` in a plain browser only sees mock events. Real Rust events
  require the Tauri runtime.
- `prefetch-updated` is emitted only by the mock bridge, never by Rust.

### Old JavaScript keeps running

Assets are cache-busted with `?v=` query strings. Bump every stamp in
`frontend/index.html`, and the `core/store.js` / `core/router.js` import stamps
in `core/shell.js` if the module graph changed. Close and reopen the app if the
webview still holds a process-level cache.

### A view renders twice

The legacy controller (`app.js`) and the Preact shell (`core/shell.js`) both
mounted the same root. There must be one owner per view; see
[frontend.md](frontend.md#1-files-and-load-order).

### The browser suite fails on connection refused

The suites expect the preview on **8143**:

```sh
PORT=8143 npm run dev
node scripts/browser_shell_test.mjs
```

The contrast gate expects **8145**. A stale server on either port makes the
next run lie. Playwright is not in `package.json`; install it locally:

```sh
npm install --save-dev playwright
npx playwright install chrome
```

### Contrast gate fails one palette

A hard-coded color bypassed the tokens. Replace it with a `tokens.css` variable
and rerun the gate. The `music` palette exists in CSS but has no settings
swatch; the default is `nebula`.

## 7. Test and CI problems

### A test deleted or pruned my real cache

This is the exact regression behind issue #48. Tests that call `prune`,
`clear`, or `usage` must set `MUSICO_CACHE_DIR` to a temp directory (the
`TestCache` helper does this). If a test ran without the guard, stop and fix the
test before rerunning. Do not point destructive tests at the real cache.

### Live tests fail with connection or timeout errors

They need network access, and some need `yt-dlp`:

```sh
cargo test -p musico-online live_ -- --ignored --nocapture
```

`MUSICO_LIVE_TESTS=1` is only a documentation convention; the code does not
read it. `--ignored` is the gate. Use a temp stream cache if you do not want
live downloads mixed with your real cache:

```sh
MUSICO_CACHE_DIR=/tmp/musico-live-cache \
  cargo test -p musico-online live_ -- --ignored --nocapture
```

### Playback tests fail in CI

`cargo test --workspace` includes `musico_playback/tests/engine_e2e.rs`, which
opens a real audio device and plays a generated tone. CI runners have neither.
CI uses `--exclude musico_playback`; run the full suite only on a desktop with
audio (and `bash scripts/gen_test_tone.sh` first).

### CI passes but the release build fails

The release workflow runs the same tests on macOS and Windows, which
type-checks platform `cfg` blocks that Linux compiles out. Use
`workflow_dispatch` to reproduce before tagging; see
[release.md §4](release.md#4-verify-the-workflow-without-tagging).

## 8. Data and settings problems

### Settings are ignored or reset

A corrupt `settings.json` loads as defaults and logs
`Corrupt settings file, using defaults`. The file is not rewritten until the
next save. Fix or delete it (app closed):

```sh
mv ~/.config/musico-online/settings.json ~/.config/musico-online/settings.json.bak
```

### The library is empty after upgrading from the Iced app

`init_recommender` checks for `db/.stable-ids`. If the marker is absent, it
clears the song index once because the id scheme changed to a stable path hash.
The next scan rebuilds it. Playlists, likes, and history are unaffected by that
one-time index clear. See
[data-storage.md §3](data-storage.md#one-time-stable-ids-migration).

### "Recommender unavailable" or a sled lock error

Only one process may open the sled DB. Close every app instance, then check for
stale processes:

```sh
pgrep -af musico-online
```

If the process list is empty and the error persists, the DB may be corrupt or
on a read-only filesystem. Back it up before deleting `db/`; deleting it resets
likes, playlists, and history.

### The DB grows without bound

Play events have no automatic retention in v0.7.0. This is normally small, but
there is no UI to trim history. Back up and reset the DB if it becomes a
problem; the library re-indexes.

## 9. Release and packaging

### A tag did not publish a release

- Tags must start with `v` and match `v*`.
- `publish` runs only when `github.ref` starts with `refs/tags/`. A
  `workflow_dispatch` run intentionally skips it.
- If a platform job fails, `publish` never runs. Open the failed job; the
  artifact upload is set to `if-no-files-found: error` for Linux/macOS.
- Tags containing `-` are prereleases. That is expected for `-online`.

### Version mismatch in the UI or bundles

Check all three version fields and `Cargo.lock` (§1 of
[release.md](release.md#1-versioning)). `cargo build` updates the lock file; do
not hand-edit it.

### The macOS or Windows artifact triggers a security warning

Expected: both are unsigned. Document the Gatekeeper/SmartScreen workaround in
the release notes rather than shipping a silently broken artifact.

## 10. What to include in a bug report

Use the issue template and include:

- version and commit (`grep '^version' src-tauri/Cargo.toml`, `git rev-parse --short HEAD`);
- OS, desktop environment, install method (dev build, deb/rpm/AppImage, DMG,
  MSI/NSIS, Android APK);
- whether the track was local, YouTube, or JioSaavn;
- exact steps and expected/actual behavior;
- the relevant log excerpt with `RUST_LOG=debug` if the failure is online;
- output of `get_platform_capabilities`, `online_available`, and
  `online_cache_usage`;
- whether Airplane Mode was on;
- a screenshot for visual issues.

Do **not** include API keys (there are none) or personal file paths you do not
want public. There is no telemetry to attach; the log is the only runtime
record.

## 11. Where to go next

- [development.md](development.md#5-logs-and-debugging) — logging and local runs.
- [online-stack.md](online-stack.md#10-failure-modes) — resolver failure model.
- [data-storage.md](data-storage.md) — paths, backup, reset.
- [testing.md](testing.md) — test layers and hermetic rules.
- [SECURITY.md](../SECURITY.md) — reporting a vulnerability instead of a bug.
