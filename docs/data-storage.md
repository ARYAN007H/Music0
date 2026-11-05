# Data and storage

Musico Online stores everything on the local machine. There is no account, no
cloud database, and no telemetry. This document describes every path the app
writes, what lives there, which parts are safe to delete, and how to back the
state up.

The single source of truth for the namespace is
`src-tauri/src/identity.rs`. The app deliberately uses `musico-online` rather
than `musico` so it can run side by side with the older offline Musico install
without sharing a sled database, cover cache, or settings file.

## 1. Storage map

Linux paths are shown; macOS and Windows resolve through the same `dirs`
calls.

| Purpose | Linux | macOS | Windows |
|---|---|---|---|
| Data root | `~/.local/share/musico-online/` | `~/Library/Application Support/musico-online/` | `%APPDATA%\musico-online\` |
| Config root | `~/.config/musico-online/` | `~/Library/Application Support/musico-online/` | `%APPDATA%\musico-online\` |
| Cache root | `~/.cache/musico-online/` | `~/Library/Caches/musico-online/` | `%LOCALAPPDATA%\musico-online\` |

```text
~/.local/share/musico-online/
├── db/                     sled database (library index, taste, playlists, batches)
│   └── .stable-ids         one-time migration marker
└── musico.log              append-only application log

~/.cache/musico-online/
├── online/                 downloaded audio streams (the prunable cache)
├── covers/                 cover thumbnails shared by local and online tracks
└── library.json            local-scan metadata cache (mtime/size keyed)

~/.config/musico-online/
└── settings.json           persisted AppSettings
```

`MUSICO_CACHE_DIR` replaces only the **stream** cache root. Covers and
`library.json` always use the normal cache root, because `scanner.rs` resolves
them independently.

## 2. `settings.json`

`src-tauri/src/config.rs` defines `AppSettings`. Every field is serde-defaulted,
so a partial or older file stays loadable. A file that fails to parse is logged
and replaced in memory by `AppSettings::default()`; the app does not rewrite it
until a setting is saved.

`save()` creates the config directory and writes pretty JSON with
`std::fs::write`. The write is not atomic: a crash mid-write can leave a
truncated file, which then loads as defaults. Back up the file before
hand-editing it.

| Field | Type | Default | Meaning |
|---|---|---|---|
| `music_folder` | string? | `null` | Last chosen local music folder |
| `volume` | f32 | `0.68` | Output volume |
| `palette` | string | `"nebula"` | Accent palette: `music`, `nebula`, `sakura`, `aurora`, `ocean`, `ember`, `mono` |
| `art_tint` | bool | `true` | Tint the player from cover art |
| `smart_recs` | bool | `true` | Use the recommender for Smart Radio / autoplay picks |
| `eq_enabled` | bool | `false` | 10-band EQ enabled |
| `eq_preset` | string | `"Flat"` | EQ preset id |
| `normalization` | string | `"Off"` | Legacy normalization mode (`Off` / `Track`) |
| `crossfade_enabled` | bool | `false` | Crossfade between tracks |
| `crossfade_duration` | f32 | `3.0` | Crossfade seconds |
| `library_view_mode` | string | `"list"` | Library layout |
| `shuffle_mode` | u8 | `0` | `0` off, `1` shuffle, `2` smart radio |
| `repeat_mode` | u8 | `0` | `0` off, `1` repeat one, `2` repeat all |
| `theme` | string | `"light"` | `light` or `dark` |
| `autoplay` | bool | `true` | Continue when the queue runs dry |
| `online_cache_mb` | u32? | `null` | Stream-cache cap; `null` means the 1 GB default |
| `offline_mode` | bool | `false` | Airplane Mode, persisted across restarts |
| `streaming_quality` | string | `"high"` | `low` / `normal` / `high` / `lossless` |
| `download_quality` | string | `"high"` | `normal` / `high` / `lossless` (and `low` in code) |
| `audio_sample_rate` | u32 | `48000` | Persisted rate hint (44100/48000/96000), reported by `get_prefetch_plan`; v0.7.0 output follows the device's native rate |
| `gapless_playback` | bool | `true` | Gapless transitions |
| `predictive_prefetch` | bool | `true` | Pre-warm upcoming/recommended tracks |
| `prefetch_count` | u8 | `3` | Tracks to pre-warm, clamped 1–5 |
| `instant_play_buffer_kb` | u32 | `64` | Progressive buffer before starting |
| `autodj_beat_sync` | bool | `true` | Beat-aligned Auto-DJ crossfade |
| `stem_mode` | string | `"full"` | `full` / `instrumental` / `vocals` |
| `loudness_norm_enabled` | bool | `true` | EBU R128 normalization |
| `target_lufs` | f32 | `-14.0` | Loudness target |
| `autoeq_profile` | string | `"flat_studio"` | Headphone AutoEQ profile id |
| `tube_warmth` | bool | `false` | Analog saturation stage |
| `lyrics_romanize` | bool | `true` | Romanized lyric overlay |
| `lyrics_translate` | bool | `true` | Translation overlay |

Notes:

- The default palette is **nebula**, matching `data-palette="nebula"` in
  `frontend/index.html` and the selected swatch in Settings. A `music` palette
  exists in `frontend/css/tokens.css` but has no settings swatch; it is only
  reachable by writing the value directly.
- `online_cache_mb` is mirrored into a process-global atomic at startup and on
  every save. `MUSICO_ONLINE_CACHE_MB` still wins over it at prune time.
- `offline_mode` is both persisted here and mirrored into `AppState.offline`
  for the fast-fail check.

## 3. sled database

Path: `<data root>/db/`. The database is opened by
`MusicRecommender::new` during `AppState::new`. If it cannot be opened, the app
still starts: the recommender is `None`, local playback and the UI work, and
taste-dependent commands return empty results.

Each domain is a named sled tree. **`open_tree` creates missing trees on open**,
so adding a tree is not a migration.

| Tree | Key | Value | Written by |
|---|---|---|---|
| *(default)* | `meta:song_count` | `u64` little-endian | `Store::increment_song_count` |
| `songs` | song id | bincode `SongRecord` | Scanner/indexer |
| `events` | `{song_id}:{event_id}` | bincode `PlayEvent` | Event bridge on `SongEnded` |
| `sessions` | `current` | bincode `SessionState` | Session start/update |
| `sessions` | `ext` | bincode `SessionExt` | Skip streak, recommendation attribution |
| `scores` | song id | bincode `SongScoreCache` | Affinity and play timestamps |
| `likes` | song id | `1` liked / `2` disliked | `set_song_like` |
| `artists` | `a:{artist}` | bincode `ArtistScoreCache` | Listen recording |
| `context` | `b:{bucket}` | bincode `ContextBucket` | Time-of-day/weekend bucket |
| `context` | `b:list` | bucket id list | Context bookkeeping |
| `clusters` | `centers` | bincode cluster centers | Clustering |
| `clusters` | `assign:{song_id}` | `u8` cluster id | Clustering / migration |
| `clusters` | `aff:{cluster}` | bincode `ClusterScore` | Listen recording |
| `lib:album` | InnerTube album id | bincode `CatalogEntry` | `library_add` |
| `lib:artist` | InnerTube artist id | bincode `CatalogEntry` | `library_add` |
| `lib:track` | video id | bincode `CatalogEntry` | `library_add` |
| `lib:playlists` | `pl-…` id | bincode `UserPlaylist` | Playlist commands |
| `playlists` | UUID | bincode `SmartPlaylist` | Read by `get_playlists`; no v0.7.0 command writes it |
| `downloads` | batch id | bincode `BatchJob` | Batch commands |

### Frozen layouts

Persisted value types (`SongRecord`, `PlayEvent`, `SessionState`,
`SessionExt`, `SongScoreCache`, `ArtistScoreCache`, `ContextBucket`,
`ClusterScore`, `CatalogEntry`, `UserPlaylist`, `PlaylistTrack`, `BatchJob`)
are **bincode**, which is positional. Unlike JSON, `#[serde(default)]` does not
make a missing field appear when decoding an older record. Adding, removing, or
reordering a persisted struct field makes every existing record undecodable.

Rules for changing a persisted type:

1. Do not change the field set of an existing persisted struct. Add a new
   struct plus a new tree or key namespace instead.
2. If a true migration is unavoidable, write a reader for the old layout,
   decode into the new type, and rewrite the tree before the new code depends
   on it.
3. Bump `FeatureVector::CURRENT_VERSION` when extraction changes; vectors from
   another version are not silently treated as comparable.
4. Add a tree only through `Store::open` or `open_tree` (auto-created), and
   document it in this table.

### One-time `stable-ids` migration

`src-tauri/src/init_recommender` opens the DB and checks for
`db/.stable-ids`. If the marker is absent, it calls `clear_song_index()` once
and writes the marker. This discards pre-stable-id song rows (the UUID-era from
the Iced app) because the current id scheme is a stable hash of the file path.
The index is rebuilt by the next scan. Do not delete the marker on a current
database: the app would clear the index again and re-embed the whole library.

### Growth

Play events have no automatic retention policy in v0.7.0. Every completed or
skipped play appends one row, so the `events` tree grows with listening
history. This is normally small (well under a kilobyte per event), but a
multi-year library with heavy use should be backed up rather than assumed
bounded. There is no UI to trim history.

## 4. `library.json`

`scanner.rs` caches local metadata so a restart does not re-probe unchanged
files. The file is a JSON array of:

```json
{
  "meta": { "id": "...", "title": "...", "artist": "...", "album": "...", "duration_secs": 0.0, "file_path": "/music/a.flac" },
  "mtime": 1700000000,
  "size": 12345678
}
```

On load it is keyed by `file_path`; a scan reuses an entry only when both mtime
and size match. A missing or unparseable file is treated as an empty cache, not
an error. The cache is rewritten after a scan.

`library.json` is derived data. Deleting it forces a metadata re-probe but does
not lose playlists, likes, or taste. It is separate from the sled `songs` tree,
which holds feature vectors and is what the recommender scores.

## 5. Cover cache

Path: `<cache root>/covers/{sanitized-id}.jpg`.

- `scanner::sanitize_cover_stem` keeps `[A-Za-z0-9:_-]` and replaces everything
  else with `_`. This blocks path traversal from a hostile catalog id or a
  crafted `get_cover` argument.
- Local covers are downscaled before writing, so steady-state RAM stays flat
  regardless of library size.
- Online covers come from `stream::cache_cover_art`: HTTPS only, host must be
  `ytimg.com` / `*.ytimg.com` / `saavncdn.com` / `*.saavncdn.com`, body capped
  at 5 MB, request timeout 8 s. An existing file is never overwritten.
- A missing cover is a valid miss. The frontend caches that miss for the
  session; restart or delete the expected file to retry.

**There is no cover-cache pruning in v0.7.0.** The 200 MB figure in earlier
planning notes is a target, not an implemented bound. Covers are small, but the
directory grows with the number of distinct tracks played. Delete it freely;
the only cost is re-fetching or re-extracting art.

## 6. Stream cache

Path: `<cache root>/online/`. Full policy is in
[online-stack.md §7](online-stack.md#7-stream-cache). Storage-relevant summary:

- One file per resolved track: `{videoId}.m4a` (yt-dlp / fast path) or
  `saavn_{id}.mp4` (JioSaavn). Namespaced ids replace `:` with `_` because
  `:` is reserved in Windows filenames; pre-existing raw `saavn:{id}.mp4`
  files remain readable.
- `.part` and `.ytdl` files are incomplete downloads. They are counted in
  usage, never treated as cache hits, and swept after 24 h by prune.
- Default cap 1 GB; `MUSICO_ONLINE_CACHE_MB` > `settings.online_cache_mb` >
  1 GB; floor 64 MB; prune target 80% by oldest mtime.
- `online_clear_cache` deletes every file in this directory and returns bytes
  freed. It does not touch covers, `library.json`, or the sled DB.
- The filesystem is the persistent truth for "downloaded". The
  `downloads` sled tree stores **batch jobs**, not per-file completion.

## 7. Startup order

`lib.rs::run` initializes storage in this order:

1. Resolve the data dir, create it, open `musico.log` for append. Logging is
   `Info` by default; `RUST_LOG` raises it.
2. `AppState::new()` loads `settings.json`, resolves the music folder
   (configured path → `dirs::audio_dir()` → `$HOME/Music` → `/tmp`), opens the
   sled DB, and applies audio settings to the engine.
3. Apply the saved stream-cache cap (the env override still wins).
4. Resume unfinished batch downloads unless Airplane Mode is on.
5. Spawn the playback event bridge.
6. Start a library scan if a folder is configured or `--scan-on-start` is
   passed.
7. Initialize platform media controls (MPRIS on Linux, SMTC on Windows,
   now-playing on macOS).

If the sled DB cannot be opened, startup continues with `recommender = None`;
if the audio engine cannot initialize, startup continues with
`engine = None` and logs a warning. Neither condition prevents the window from
opening.

## 8. Backup and restore

Back up the whole data and config roots while the app is **closed**:

```sh
# Linux
tar czf musico-online-backup.tgz \
  ~/.local/share/musico-online \
  ~/.config/musico-online
```

The sled DB is a live log-structured store; copying it while the app is running
can produce a torn snapshot. The cache root is optional: streams and covers are
re-downloadable, and `library.json` is rebuilt by a scan.

Restore by extracting with the app closed, preserving ownership and
permissions. The `db/.stable-ids` marker must travel with the DB.

## 9. Reset

| Goal | Action |
|---|---|
| Reclaim stream space | Storage settings → Clear cache, or delete `<cache>/online/` |
| Force cover re-fetch | Delete `<cache>/covers/` |
| Force a metadata re-probe | Delete `<cache>/library.json`, then rescan |
| Reset all settings | Delete `<config>/settings.json` (app closed) |
| Reset taste/playlists/library index | Delete `<data>/db/` (app closed) |
| Reset everything | Delete all three roots |

Deleting `db/` removes likes, playlists, play history, saved albums/artists,
and batch jobs. It does **not** touch audio files in the music folder. The app
recreates the DB and starts a fresh session on next launch; the next scan
re-indexes the library.

Do not delete `musico.log` while the app is running on Windows; close the app
first.

## 10. Privacy

- No telemetry, analytics, crash reporting, or remote logging.
- Outbound requests go to the catalog/stream sources described in
  [online-stack.md](online-stack.md), plus LRCLIB for lyrics and the configured
  Piped/Invidious/Saavn hosts. Those services see your IP and the queries you
  make, as any client would.
- Listening history, likes, playlists, and settings never leave the machine.
- The web preview (`server.ts`) is a separate demo runtime with its own
  in-memory state; it does not read or write the desktop storage paths.

## 11. Where to go next

- [online-stack.md](online-stack.md) — cache and resolver internals.
- [ipc.md](ipc.md) — commands that read or mutate this data.
- [development.md](development.md) — settings and environment variables.
- [troubleshooting.md](troubleshooting.md) — corrupt DB, cache, and settings.
