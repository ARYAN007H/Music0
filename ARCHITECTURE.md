# Architecture

> **Status:** describes Musico Online **v0.7.0** (`v0.7.0-online`, 2026-11-05).
> **Audience:** contributors, reviewers, and anyone debugging the runtime.
> **Scope:** the desktop/Android Tauri application. The web/PWA preview has its
> own runtime and is documented separately in [Two runtimes](#two-runtimes).

Musico Online is a **local-first, single-user music player**. There is no
account system, no cloud backend, and no telemetry. The app talks directly to
public catalog and stream sources, keeps taste data in a local sled database,
and plays everything through one shared audio engine.

---

## 1. System at a glance

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  WebView (WebKitGTK · WKWebView · WebView2)                                  │
│                                                                              │
│  frontend/                                                                   │
│    index.html ── classic scripts ──► bridge.js · app.js · online.js · vinyl.js│
│                         │                    │                               │
│                         │            core/store.js ◄──► core/shell.js (Preact)│
│                         │            core/router.js                          │
│                         ▼                                                    │
│                 Bridge.invoke(cmd, args)     Bridge.listen(event, cb)         │
└─────────────────────────┬────────────────────────────────────────────────────┘
                          │ Tauri IPC (JSON over the webview bridge)
┌─────────────────────────▼────────────────────────────────────────────────────┐
│  Native process: musico-online (Rust, Tauri 2)                                │
│                                                                              │
│  commands.rs ── thin IPC layer (86 commands)                                  │
│  state.rs ───── AppState: engine, queue, library, settings, recommender       │
│  event_bridge.rs ── 50 ms poller + auto-advance policy + platform media       │
│  scanner.rs ──── library scan, metadata, cover cache, background indexing     │
│  online/ ─────── InnerTube · JioSaavn · Piped/Invidious · yt-dlp · lyrics     │
│  config.rs / identity.rs / lyrics.rs / mpris.rs / media_controls.rs           │
│                                                                              │
│  ┌────────────────────────────┐      ┌─────────────────────────────────────┐  │
│  │ musico_playback            │      │ musico_recommender                  │  │
│  │ decoder · CPAL output ·    │      │ sled DB · feature extraction ·      │  │
│  │ ring buffer · EQ · DSP ·   │      │ taste profile · clusters · mixes ·  │  │
│  │ crossfade · queue          │      │ playlists · batch download records  │  │
│  └────────────────────────────┘      └─────────────────────────────────────┘  │
└──────────┬──────────────────────────────────┬────────────────────────────────┘
           │                                  │
           ▼                                  ▼
  Local files & app data              Public network sources
  ~/.local/share/musico-online        music.youtube.com (InnerTube)
  ~/.cache/musico-online              Piped / Invidious instances
  ~/.config/musico-online             JioSaavn · LRCLIB
                                      yt-dlp child process (fallback)
```

### Two runtimes

The repository intentionally contains **two ways to run the UI**. They share
the frontend assets but not their backend implementation.

| Runtime | Entry point | Backend | Purpose |
|---|---|---|---|
| **Desktop / Android** (production) | `src-tauri/src/main.rs` → `musico_online_lib::run()` | Rust: playback + recommender + online stack | The shipped app: local files, real streaming, persistent library, system media controls |
| **Web / PWA preview** (development + demo) | `server.ts` (Express) | TypeScript: `src/engine/*` with a seeded catalog and synthesized audio | Browser preview, PWA/mobile demo, browser assertion suites. **Not** feature-complete and **not** the production backend |

Do not assume a fix in `src/engine/*` reaches the desktop app, or vice versa.
When a behavior must match, change both and add a test on each side.

---

## 2. Design principles

1. **Local-first by default.** Library metadata, playlists, likes, listening
   history, and learned weights live on disk. Online catalog pages are cached;
   the app remains usable when the network disappears.
2. **One audio path.** Local files, cached streams, and freshly downloaded
   streams all become a `SongInfo` with a file path and go through
   `musico_playback`. There is no second streaming player.
3. **Thin IPC.** `commands.rs` validates, dispatches, and returns. Business
   logic lives in the crates or `online/` modules.
4. **Never block the UI.** Network, yt-dlp, scanning, and indexing run on
   worker threads (`spawn_blocking` or named OS threads). Commands that can
   take more than a few milliseconds are `async`.
5. **Degrade honestly.** A failed source falls through to the next one; a
   failed page keeps a good stale copy; an unavailable feature returns a typed
   error or an empty state, never a panic. The user sees a message, not a
   spinner forever.
6. **Isolate drift.** All InnerTube response parsing lives in
   `online/parse.rs`. A YouTube shape change should be a one-file patch.
7. **No build step for the frontend.** Vanilla ES modules plus vendored
   Preact/htm. `index.html` is directly loadable; there is no bundler,
   transpiler, or generated asset pipeline.
8. **Bounded work.** Every retry loop, cache, queue, and speculative download
   has a cap. "Retry forever" is considered a bug.

---

## 3. Repository map

```text
.
├── src-tauri/                 Tauri app crate (binary + Android cdylib)
│   ├── src/
│   │   ├── main.rs            Desktop entry
│   │   ├── lib.rs             Shared desktop/Android setup + command registry
│   │   ├── commands.rs        IPC commands (thin) — shared hotspot
│   │   ├── state.rs           AppState + SongMeta
│   │   ├── event_bridge.rs    Playback event loop, auto-advance, prefetch, radio warmup
│   │   ├── scanner.rs         Library scan + metadata/cover cache + indexer
│   │   ├── config.rs          Persisted AppSettings
│   │   ├── identity.rs        Paths, D-Bus name, display name
│   │   ├── lyrics.rs          Sidecar .lrc parser
│   │   ├── online/            Catalog + stream stack (see docs/online-stack.md)
│   │   ├── mpris.rs           Linux MPRIS (D-Bus)
│   │   └── media_controls.rs  macOS/Windows system media controls
│   └── tauri.conf.json        Window, bundle, identifier
├── musico_playback/           Audio engine crate (no Tauri dependency)
├── musico_recommender/        Offline recommender crate (no network)
├── frontend/                  No-build UI + PWA assets
│   ├── index.html             View markup + script order
│   ├── css/                   Tokens → base → feature layers
│   ├── js/
│   │   ├── bridge.js          Tauri IPC + mock fallback
│   │   ├── app.js             Legacy controller: state, views, transport
│   │   ├── online.js          Browse/search UI + online playback
│   │   ├── vinyl.js, seek.js, knob.js
│   │   └── core/              store.js · router.js · shell.js (Preact/htm)
│   ├── vendor/htm-preact.js   Vendored Preact + htm, loaded as ESM
│   ├── manifest.json          PWA manifest
│   └── sw.js                  PWA shell cache
├── src/engine/                TypeScript engine for the web/PWA runtime + tests
├── server.ts                  Express web preview + demo RPC + synthesized audio
├── scripts/                   Browser suites, contrast gate, test-tone generator
├── docs/                      Deep-dive documentation (index in docs/README.md)
├── .github/workflows/         CI (tests) and Release (Linux/macOS/Windows)
├── ARCHITECTURE.md            This file
├── CONTRIBUTING.md            How to contribute
├── SECURITY.md                Threat model + reporting
├── CHANGELOG.md               Release history
└── ROADMAP.md                 Plan of record
```

---

## 4. Process and threading model

The native app is **one process** with a small, fixed set of thread classes.
The rule that prevents most regressions: *audio and UI are sacred; everything
else waits.*

| Thread / executor | Owned by | Work | Rules |
|---|---|---|---|
| **Main / UI thread** | Tauri | Window, webview, IPC dispatch, menu, commands that return quickly | Never perform network I/O, file scans, or decoder work here |
| **Tauri async runtime** | Tauri (Tokio) | `async` commands; bridge to blocking work via `spawn_blocking` | Keep command bodies thin |
| **`musico-decoder`** | `musico_playback` | Decode packets, resample, apply EQ/DSP, fill ring buffer, emit events | Owns all decode state; communicates by channels |
| **CPAL audio callback** | OS audio thread, via `cpal` | Pop samples from the ring buffer, scale volume, count played samples | Real-time: no allocation, no blocking, no locks |
| **`playback-event-bridge`** | `event_bridge.rs` | Poll events every 50 ms; forward to UI; run auto-advance; update MPRIS/SMTC | Runs for the process lifetime |
| **`indexer` / scan worker** | `scanner.rs` | Metadata probe, cover cache, feature extraction for the recommender | One scan at a time (`scanning` flag) |
| **`online-preresolve`, `predictive-prefetch`, `radio-warm`, `online-radio`, `online-cover`** | `event_bridge.rs` / `online/stream.rs` | Speculative downloads and cover fetches | Fire-and-forget; bounded budgets; single-flight guards |
| **`online-batch`** | `commands.rs` | Persistent album/playlist downloads, ≤ 2 concurrent | One runner per batch id; registry prevents double runs |
| **`browse-refresh`, `genres-refresh`** | `online/catalog.rs` | Stale-while-revalidate background refresh | One refresh per cache key |
| **MPRIS command loop** | `mpris.rs` (Linux) | D-Bus media-key commands | Dedicated OS thread |

### Concurrency rules

- **Network requests are globally throttled.** InnerTube allows at most two
  in-flight requests (`client::MAX_CONCURRENCY`), two retry attempts, and
  exponential backoff with jitter.
- **Identical in-flight requests coalesce.** Two identical InnerTube POSTs
  share one round trip; waiters do not hold a throttle permit.
- **Downloads are serialized per video.** A `video_lock(video_id)` makes
  duplicate downloads wait, then hit the cache.
- **yt-dlp children are capped process-wide at three.** Batch downloads
  self-limit to two concurrent resolves; interactive play, pre-resolve, and
  radio warmup would otherwise stack.
- **Speculative work is gated.** Radio warmup waits 15 s and requires the
  track to still be playing; predictive prefetch is bounded to
  `prefetch_count` (1–5).
- **Batch persistence is the source of truth for unfinished downloads.** The
  in-memory runner registry only prevents two workers from mutating the same
  batch record.

---

## 5. Crate and module boundaries

### `src-tauri` (`musico-online`)

The composition root. It owns Tauri state, the command registry, platform
integration, and the online catalog. It depends on the two library crates but
they do **not** depend on it.

- `commands.rs` is append-only by convention. New commands are registered in
  `lib.rs` and implemented here.
- `event_bridge.rs` owns the **advance policy** (see §7.3), media integration,
  and speculative prefetch triggers.
- `online/` is a vertical slice; see [docs/online-stack.md](docs/online-stack.md).
- `identity.rs` is the single source of truth for on-disk names. Do not
  hard-code `musico-online` paths elsewhere.

### `musico_playback`

A standalone audio crate with **no Tauri dependency**, so it can be tested and
reused independently.

```text
PlaybackEngine (public handle)
  ├── crossbeam command channel ──► musico-decoder thread
  │                                   ├── Symphonia decoder
  │                                   ├── rubato resampler
  │                                   ├── 10-band EQ + stem/tube DSP
  │                                   └── ring buffer producer
  ├── CPAL output stream ◄──────────── ring buffer consumer (audio callback)
  ├── shared PlaybackState (Arc<Mutex>)
  └── crossbeam event channel ─────► poll_events() ──► event_bridge
```

Key invariants:

- `PlaybackEngine` is internally synchronized; it lives behind a plain `Arc`.
- The engine only knows the **current** song. Queue navigation belongs to the
  app layer (`PlaybackQueue` is a helper, not engine state).
- Seek and stop flush the ring buffer; pause gates the callback so resume is
  instant.
- Position is derived from samples the callback actually played, not from how
  far the decoder has read ahead.

### `musico_recommender`

A 100% offline recommendation crate. It never makes network calls and never
knows about Tauri or the UI.

- sled database with one tree per domain (see
  [docs/data-storage.md](docs/data-storage.md)).
- Feature extraction (MFCC, spectral centroid/rolloff, ZCR, RMS, tempo, chroma)
  with a background indexer.
- Seven weighted signals (similarity, profile, session, artist, cluster,
  context, discovery) with online weight learning, MMR diversification, and a
  per-artist cap.
- Library entries, user playlists, smart playlists, batch-download records,
  stats, and listening history.

### `frontend/`

A deliberately small, no-build UI. Three layers coexist:

1. **Classic scripts** (`bridge.js`, `app.js`, `online.js`, `vinyl.js`,
   `seek.js`, `knob.js`) publish globals (`window.Bridge`, `window.App`,
   `window.Online`). `app.js` remains the controller for state and rendering.
2. **ES modules** (`core/store.js`, `core/router.js`, `core/shell.js`) provide
   the observable store, hash router with a real history stack, and the
   Preact/htm shell (sidebar + top transport bar).
3. **CSS** is layered: `tokens.css` defines semantic variables; feature sheets
   (`layout`, `now-playing`, `library`, `queue`, `online`, `settings`, …) style
   their areas; `am-clean.css`, `liquid.css`, `apple.css`, and `native.css`
   refine the visual system and platform chrome. Later files win by cascade.

The store is the meeting point: `app.js` publishes snapshots, Preact
components subscribe. `core/shell.js` calls back into `window.App` for
transport actions so there is exactly one controller.

See [docs/frontend.md](docs/frontend.md) for the full guide.

### `src/engine/*` and `server.ts`

A **separate TypeScript implementation** used by the web/PWA runtime and the
Node test suite. It mirrors product concepts (hybrid recommender, quality
tiers, taste DNA, jam sessions, AutoEQ metadata) but is not wired into the
desktop Rust backend. Treat changes here as web-runtime changes.

---

## 6. IPC contract

The webview talks to Rust through Tauri's `invoke`/`emit` bridge.
`frontend/js/bridge.js` wraps both:

- `Bridge.invoke(cmd, args)` returns the command result.
- In a browser (no `__TAURI_INTERNALS__`), it returns stateful mock data so
  `index.html` works as a standalone preview.
- On IPC rejection, the bridge logs and returns `null`. **`null` is the
  frontend's failure convention.** Commands that would otherwise serialize
  `Ok(())` as `null` return `true` or a value instead.
- `Bridge.listen(event, cb)` returns an unsubscribe function. A failed listener
  registration logs loudly; it does not silently swallow the channel.

### 6.1 Command groups

The registry is `src-tauri/src/lib.rs`. The complete index is in
[docs/ipc.md](docs/ipc.md); this is the shape:

| Group | Commands |
|---|---|
| **Library** | `get_library`, `start_scan`, `get_cover`, `search_library` |
| **Playback** | `play_song`, `pause`, `resume`, `seek`, `set_volume`, `toggle_mute`, `get_playback_state`, `next`, `previous`, `set_playback_modes`, `set_autoplay` |
| **Queue** | `get_queue`, `add_to_queue`, `play_next`, `remove_from_queue`, `clear_queue`, `queue_move`, `queue_play_at` |
| **Settings** | `get_settings`, `save_settings`, `pick_folder`, `set_music_folder` |
| **Recommendations** | `get_recommendations`, `recommended_for_home`, `set_song_like`, `get_liked_ids` |
| **Online catalog** | `online_search`, `online_search_suggestions`, `online_home`, `online_browse`, `online_genres`, `online_related` |
| **Online playback** | `online_play`, `online_enqueue`, `online_play_next`, `online_start_radio` |
| **Downloads / offline** | `online_download`, `online_download_batch`, `online_batches`, `online_batch_retry`, `online_batch_cancel`, `set_offline`, `get_offline`, `online_cache_usage`, `set_online_cache_cap`, `online_clear_cache`, `online_available` |
| **Saved library** | `library_add`, `library_remove`, `library_list`, `library_contains` |
| **Playlists** | `playlist_create`, `playlist_list`, `playlist_rename`, `playlist_delete`, `playlist_add_track`, `playlist_remove_track`, `playlist_move_track` |
| **Lyrics** | `online_lyrics`, `online_synced_lyrics`, `get_lyrics`, `get_enhanced_lyrics` |
| **Stats / smart lists** | `get_stats`, `get_playlists` |
| **DSP / Sonic Lab** | `set_eq`, `set_normalization`, `set_stem_mode`, `set_loudness_norm`, `get_autoeq_profiles`, `apply_autoeq_profile`, `get_prefetch_plan`, `get_autodj_plan`, `get_taste_dna`, `apply_taste_dna`, `build_sonic_bridge`, `get_daypart_capsule`, `execute_sonic_command`, `get_platform_capabilities` |
| **Jam sessions** | `create_jam_session`, `join_jam_session`, `vote_jam_track`, `get_jam_session` |

### 6.2 Events

| Event | Emitted by | Payload | Consumer |
|---|---|---|---|
| `playback-event` | `event_bridge.rs` | Tagged enum: `Playing`, `Paused`, `Resumed`, `Stopped`, `Seeked`, `PositionUpdate`, `SongEnded`, `BufferingStarted`, `BufferingComplete`, `Error`, `VolumeChanged` | `app.js`, `online.js`, shell |
| `queue-changed` | commands / advance | `SongMeta[]` | `app.js` queue panel |
| `scan-progress` | `scanner.rs` | Scan counters | Settings / library |
| `library-updated` | `scanner.rs` | `SongMeta[]` | `app.js` |
| `index-progress` / `index-complete` | `scanner.rs` | Index counters | UI status |
| `online-status` | online commands | `{stage, videoId, title, message?}` where stage is `resolving`, `playing`, `queued-next`, `radio`, `error` | `online.js` |
| `download-state` | `online_download` | `{videoId, stage: resolving\|done\|failed, title, message?}` | `online.js` |
| `download-progress` | batch worker | `{batchId, label, done, failed, total, state}` | downloads UI |
| `prefetch-updated` | browser mock only (`frontend/js/bridge.js`); Rust never emits it | Empty marker | prefetch panel |

### 6.3 IPC conventions

- **Argument names are camelCase on the JS side** and snake_case in Rust;
  Tauri maps them. Watch for commands where the frontend intentionally sends
  `positionSecs` vs `position_secs` — tests pin the contract.
- **Return values are meaningful.** `true`/`false` or an object beats `null`
  when the frontend must distinguish success.
- **Long operations return quickly and report progress by event.** Do not hold
  a command open for a multi-minute batch.
- **Airplane Mode fast-fails.** Every network-reaching command calls
  `ensure_online()`; batch downloads persist as pending until the mode lifts.
- **Track objects round-trip verbatim.** `OnlineTrack` includes `source` and
  `artist_browse_id`; the frontend sends the same object back on play/enqueue.

---

## 7. Core flows

### 7.1 Library scan and indexing

```text
start_scan
  → scanner::start_scan (single-flight via AppState.scanning)
  → walk music folder (supported extensions)
  → per-file: probe metadata + embedded art (Symphonia)
      → cache hit by (mtime, size) skips re-probe
      → cover written to ~/.cache/musico-online/covers/{id}.jpg
  → emit scan-progress periodically
  → publish library-updated with Vec<SongMeta>
  → spawn indexer thread:
      → extract feature vector
      → store SongRecord + cluster assignment
      → emit index-progress / index-complete
```

Local song IDs are **stable path hashes** (`stable_song_id`), not random UUIDs.
Library, recommender records, play events, and cover filenames all agree on
that ID. A one-time migration marker clears pre-stable-ID databases.

### 7.2 Playback

```text
play_song / online_play / next / previous
  → command validates and builds a SongInfo { id, file_path, title, … }
  → engine.play(song) sends PlaybackCommand::Play over crossbeam
  → decoder thread opens the file (Symphonia), emits Playing
  → decoded packets → resample → EQ/DSP → ring buffer
  → CPAL callback consumes; position derived from played samples
  → events travel back over the event channel
  → event_bridge forwards playback-event to the webview every 50 ms
```

`event_bridge::play_and_track()` also:

- notifies the recommender of the song change;
- auto-indexes online tracks (`yt:` / `saavn:`) in the background;
- applies ReplayGain/LUFS normalization;
- preloads the next track when gapless is enabled;
- pre-resolves the next queued online track;
- predictively prefetches up to `prefetch_count` upcoming online tracks;
- warms the next radio pick when appropriate.

### 7.3 Auto-advance policy

`event_bridge::advance_from()` runs on `SongEnded` and on explicit `next`:

1. **Explicit queue.** Pop the next entry. Missing files are skipped quietly,
   up to 5, then fall through.
2. **Repeat One.** Replay the current song.
3. **Online radio.** If the current song is `yt:`, the queue is empty,
   autoplay is on, and Airplane Mode is off: fetch related tracks on a worker
   thread. A warmed pick becomes a cache hit; a live fetch is the fallback.
4. **Smart Radio.** If smart recommendations and (Smart Shuffle or autoplay)
   are on: play the top local recommendation anchored on the current song.
5. **Shuffle / Repeat All.** Pick a random local library song.
6. **Stop.** Reset the UI.

The online-radio path is gated by `autoplay` (C1.3). The README/ROADMAP notes
that said otherwise predate the gate and are kept only as history.

### 7.4 Online search

```text
online_search(query, videos?, continuation?)
  → ensure_online()
  → spawn_blocking:
      search::search(query, videos, continuation)
        → normalized cache key (case/whitespace collapsed), 5 min TTL, 64 entries
        → InnerTube WEB_REMIX search (Songs or Videos filter)
        → parse_search_page (tolerant per-item parsing)
        → log a warning if a first page parses to zero results
      first page only, songs only:
        → saavn::search_songs(query, SEARCH_MERGE_LIMIT)
        → append independent JioSaavn results (failures become [])
  → return { tracks, next_continuation }
```

The frontend debounces live search (350 ms), typeahead (200 ms), and guards
against out-of-order responses with a sequence number.

### 7.5 Catalog browse

`online_browse(browse_id, params)` is generic: the UI follows navigation
endpoints found in items, so new shelf types do not require client changes.

Cache policy is **stale-while-revalidate**:

- fresh for 5 minutes;
- served stale up to 6 hours while exactly **one** background refresh runs per
  `(browse_id, params)` key;
- an empty re-parse never overwrites a good stale copy;
- Home additionally merges editorial charts (`FEmusic_charts`) when the
  personalized feed is sparse, so a first run is never a blank screen.

### 7.6 Stream resolution

Resolution is tiered. Every step writes the same cache file and verifies it
before playback.

```text
resolve_track(track, interactive)
  │
  ├─ source == "saavn" ──► JioSaavn direct URL (decrypt → CDN → verify)
  │                        no yt-dlp, honors streaming/download quality tier
  │
  └─ YouTube track
       ├─ cache hit? ──► return file
       ├─ JioSaavn cross-source exact match (title + artist + duration ±4 s)
       │     └─ hit ──► direct CDN file
       └─ resolve_with (per-video lock)
            ├─ fast path: Piped → Invidious → InnerTube Direct
            │     pool health-ordered by EWMA; ≈10 s shared budget
            │     https-only, audio content-type gate, 64 MB cap,
            │     `.part` → fsync → atomic rename, ≥10 KB verify
            └─ yt-dlp fallback
                  quality selector (low / normal / high / lossless)
                  interactive: 1 attempt, 90 s total
                  background: 2 attempts × 120 s
                  process-wide cap: 3 children
                  timeout → kill + reap; output nulled to avoid pipe wedge
```

After a successful download:

- the file is verified (`exists`, ≥ 10 KB);
- duration is probed with Symphonia when metadata lacked it;
- the thumbnail is fetched into the shared cover cache (https + allowlist +
  5 MB cap);
- the LRU cache is pruned (default 1 GB, target 80% after pruning).

### 7.7 Downloads, batches, and offline mode

- **Single download** (`online_download`): session state machine
  `resolving → done | failed(reason)`. Retrying means invoking again. The
  filesystem is the persistent truth.
- **Batch download** (`online_download_batch`): persisted in the sled
  `downloads` tree; ≤ 2 concurrent resolves; resumable after restart;
  retry-failed and cooperative cancel; progress via `download-progress`.
  Batch currently supports YouTube tracks only.
- **Airplane Mode** (`set_offline`): persists `offline_mode`, fast-fails online
  commands, avoids futile speculative work, and defers batch resume. Lifting
  the mode resumes every pending batch in one sequential worker.

### 7.8 Lyrics

`get_enhanced_lyrics` resolves metadata from the engine, library, then queue,
and tries sources in order:

1. **Local sidecar `.lrc`** for local files (synced first, plain fallback).
2. **LRCLIB** synced lyrics matched by artist + title + duration (plain
   fallback).
3. **YouTube Music InnerTube** plain lyrics for `yt:` tracks.

Local `.lrc` files may contain `primary | [romanized] | (translation)`; the
backend synthesizes word-level timestamps across each line span for the
karaoke view. Offline mode stops after the local sidecar.

---

## 8. State, persistence, and caches

Full detail: [docs/data-storage.md](docs/data-storage.md). The short version:

| Location | Contents | Owner |
|---|---|---|
| `~/.local/share/musico-online/db/` | sled DB: songs, events, sessions, scores, likes, artists, context, clusters, saved library, playlists, batches | `musico_recommender` |
| `~/.local/share/musico-online/musico.log` | Append-only application log | `env_logger` |
| `~/.cache/musico-online/online/` | Downloaded/cached stream files (`.m4a`/`.mp4`, plus transient `.part`/`.ytdl`) | `online/stream.rs` |
| `~/.cache/musico-online/covers/` | Cover thumbnails keyed by sanitized song id | `scanner.rs` / `online/stream.rs` |
| `~/.cache/musico-online/library.json` | Scan cache keyed by path, mtime, size | `scanner.rs` |
| `~/.config/musico-online/settings.json` | `AppSettings` JSON with serde defaults | `config.rs` |

Cache tiers:

1. **Memory** — parsed search pages (5 min, 64 entries), browse pages (48
   entries), genre list, stream-cache index, radio recent ring buffer, warm
   pick slot.
2. **Disk art** — cover cache, shared by local files and online tracks.
3. **Disk streams** — LRU by mtime with hysteresis.
4. **Database** — durable library, history, taste state, playlists, batches.

Environment overrides (all optional):

| Variable | Effect |
|---|---|
| `MUSICO_ONLINE_CACHE_MB` | Stream-cache cap in MB (wins over the settings slider) |
| `MUSICO_CACHE_DIR` | Replaces the stream-cache root entirely (tests; not covers) |
| `MUSICO_PIPED_INSTANCES` | Comma-separated Piped hosts; empty disables the kind |
| `MUSICO_INVIDIOUS_INSTANCES` | Comma-separated Invidious hosts |
| `MUSICO_INNERTUBE_DIRECT` | Comma-separated InnerTube Direct hosts |
| `MUSICO_SAAVN_API` | JioSaavn API base (proxy/mirror) |
| `MUSICO_LRCLIB_API` | LRCLIB API base (proxy/mirror) |
| `RUST_LOG` | Rust log filter; when set, logs also go to stderr |
| `PORT` | Web preview port (`server.ts`) |

Precedence for the stream cap: **env > settings slider > 1 GB default**.

---

## 9. Failure model

| Failure | Behavior |
|---|---|
| No audio device | Engine initialization fails; app still opens; playback commands return an honest error |
| Local file missing | Auto-advance skips up to 5 dead queue entries; UI does not wedge |
| InnerTube search shape drift | Parser tolerates per item; warns on zero results; stale cache where available |
| InnerTube request failure | 1 retry with jittered backoff; coalesced waiters recover independently |
| Piped/Invidious/Direct all fail | yt-dlp fallback; if that fails, user-visible error |
| yt-dlp missing | Fast path still works; if it also fails, error names the install command |
| yt-dlp timeout | Child killed and reaped; `.part` swept later; retry semantics bounded |
| Bad/empty catalog parse | Good stale copy served; empty parse never cached |
| Cover fetch fails | Playback proceeds without art; no retry loop |
| Malicious/hostile catalog id | ID charset gates and path sanitization prevent traversal |
| Disk full | Download fails cleanly, `.part` removed, error surfaced |
| Second app instance | Single-instance plugin focuses the existing window before DB/audio access |
| Airplane Mode + online command | Immediate typed error (< 10 ms) instead of a network hang |
| Web listener registration fails | `Bridge.listen` logs the channel as permanently dead; no silent data loss |

---

## 10. Security boundaries

The full threat model is in [SECURITY.md](SECURITY.md). Architecturally:

- **Trusted:** the local binary, local music files, the bundled frontend.
- **Untrusted:** every HTTP response (InnerTube, Piped, Invidious, JioSaavn,
  LRCLIB), thumbnails, and yt-dlp's output. IDs, URLs, and filenames derived
  from them are validated.
- **Not a boundary:** the webview. The frontend is loaded locally, but any
  injected content must still not be able to escape the app's data dirs.
- **Dev-only:** `server.ts` binds `0.0.0.0` and serves a demo. Never expose it
  to an untrusted network.

Hardening already in place: https-only art allowlist (`*.ytimg.com`,
`*.saavncdn.com`), response size caps, audio content-type gates, exact cache
prefix matching, path traversal sanitization, per-video locks, child-process
caps, and no shell interpolation when spawning yt-dlp.

---

## 11. Performance budgets

These are the working targets from `ROADMAP.md §0`; treat regressions as bugs.

| Budget | Target |
|---|---|
| Cold start → Home interactive | < 1.5 s cached / < 4 s fresh |
| Click → sound | < 150 ms cached / < 4 s p95 fresh |
| Cached online advance | < 1 s (measured ≈ 9 µs for a warm radio pick) |
| Repeat search (case/whitespace variant) | < 100 ms (cache hit) |
| RAM steady, 10k-row library | < 250 MB |
| Idle CPU | < 1.5% |
| Stability | zero uncaught panics in an 8 h soak |
| Stream cache | ≤ 1 GB default, prunes to 80% after limit |
| Art cache | 5 MB per file; no directory prune in v0.7.0 |

---

## 12. Extending the system

### Add an IPC command

1. Implement `#[tauri::command]` in `src-tauri/src/commands.rs` (append-only
   region; do not reformat neighbors).
2. Register it in the `generate_handler![]` list in `lib.rs`.
3. Return a meaningful value, not `Ok(())`, when the UI needs success/failure.
4. If it does network or disk work, make it `async` + `spawn_blocking`, or
   spawn a named worker.
5. Add the wrapper in `frontend/js/bridge.js` mock mode if the web preview must
   exercise it.
6. Document it in [docs/ipc.md](docs/ipc.md).

### Add a view

1. Add a `<section id="view-…" class="view">` in `frontend/index.html` (or
   render it from the Preact shell).
2. Add the nav item in `core/shell.js` if it belongs in the sidebar.
3. Handle the route in `app.js` (`_activateView`) and, if new, in
   `core/router.js`.
4. Add styles in the appropriate CSS layer, using `tokens.css` variables.
5. Bump every `?v=` in `index.html` (and the service-worker cache name) — see
   [docs/frontend.md](docs/frontend.md#6-cache-busting).
6. Add a browser assertion to the closest `scripts/browser_*_test.mjs`.

### Add a stream source

1. Implement the `AudioSource` trait in `online/sources.rs`.
2. Add it to `SourceKind`, `default_pool()`, and the environment override.
3. Keep the trust gates: https, content-type, size cap, atomic rename,
   minimum-size verify.
4. Unit-test failover with the injectable source list (no network).
5. Add a `#[ignore]` live test for the real endpoint.

### Add a platform integration

- Linux: `mpris.rs` (D-Bus).
- macOS/Windows: `media_controls.rs` (souvlaki).
- Window chrome is split between `tauri.conf.json`, `core/shell.js`, and
  `native.css` (`is-mac` / `is-windows` classes set pre-paint).
- Gate platform-only code with `#[cfg(target_os = …)]` and make sure the Linux
  CI still compiles the crate.

---

## 13. Architectural decisions

The short records live in [docs/adr/](docs/adr/):

- [ADR-0001 — Tauri 2 with a no-build frontend](docs/adr/0001-tauri-2-no-build-frontend.md)
- [ADR-0002 — Separate app identity and data namespace](docs/adr/0002-separate-app-identity.md)
- [ADR-0003 — Tiered stream resolution](docs/adr/0003-tiered-stream-resolution.md)
- [ADR-0004 — Local-first recommender](docs/adr/0004-local-first-recommender.md)

---

## 14. Known fragile areas

- **InnerTube is undocumented.** Response shapes change without notice. All
  parsing is in `online/parse.rs`; a release should run the live tests.
- **Public instance ecosystems churn.** Piped/Invidious hosts disappear or
  bot-wall. Failover is honest, but the pool needs periodic live checks.
- **yt-dlp needs updates.** When YouTube changes extraction, the fallback
  breaks until yt-dlp is updated. The fast path reduces exposure but does not
  eliminate it.
- **JioSaavn media URLs are encrypted with a fixed public key.** This is
  metadata decoding, not DRM circumvention; the implementation must stay
  scoped to that.
- **The frontend has two worlds.** `app.js` (classic script, global state) and
  `core/shell.js` (Preact, store-driven) meet at `MusicoStore`. Avoid
  introducing a third source of truth.
- **`commands.rs` is a shared hotspot.** Append; do not reorder or reformat.
- **Android is compiled but not exercised by CI.** Pure-Rust resolvers exist
  specifically so Android can play without yt-dlp, but device testing is
  manual.
- **Browser suites are not in CI.** They require a local server and Playwright;
  run them before merging frontend work.

---

## 15. Related docs

| Document | What it covers |
|---|---|
| [CONTRIBUTING.md](CONTRIBUTING.md) | Setup, workflow, standards, review gates |
| [docs/development.md](docs/development.md) | Day-to-day development and debugging |
| [docs/testing.md](docs/testing.md) | Test layers, live tests, browser suites |
| [docs/frontend.md](docs/frontend.md) | No-build UI, store/router/shell, CSS, cache busting |
| [docs/online-stack.md](docs/online-stack.md) | Catalog, sources, resolution, radio, lyrics |
| [docs/data-storage.md](docs/data-storage.md) | Paths, sled trees, caches, resets |
| [docs/ipc.md](docs/ipc.md) | Command index and event payloads |
| [docs/release.md](docs/release.md) | Versioning, CI, artifacts, signing |
| [docs/troubleshooting.md](docs/troubleshooting.md) | Common failures and fixes |
| [SECURITY.md](SECURITY.md) | Threat model and vulnerability reporting |
| [ROADMAP.md](ROADMAP.md) | Product plan and acceptance gates |
