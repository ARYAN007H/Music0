# IPC reference

The webview talks to Rust through Tauri's `invoke`/`emit` bridge. This
document is the command and event catalog for **v0.7.0**. The registry is
`src-tauri/src/lib.rs` (`tauri::generate_handler!`); handlers live in
`src-tauri/src/commands.rs`.

There are **86 registered commands** and **9 Rust-emitted event channels**.
`prefetch-updated` exists only in the browser mock (`frontend/js/bridge.js`)
and is not emitted by the Rust backend.

## 1. Conventions

### Names and arguments

- Command names are `snake_case` and must be registered exactly once.
- Rust parameters are `snake_case`; Tauri exposes them to JS as
  **`camelCase`**. `song_id` becomes `songId`, `duration_secs` becomes
  `durationSecs`, `browse_id` becomes `browseId`.
- Optional Rust `Option<T>` parameters may be omitted or passed as `null`.
- Struct arguments are plain objects. `SongMeta` and `OnlineTrack` are sent
  back **verbatim**; do not drop fields such as `source` or the backend loses
  the ability to dispatch resolution.

```js
const page = await Bridge.invoke('online_search', { query: 'Believer', videos: false });
await Bridge.invoke('set_song_like', { songId: 'yt:abc123', liked: true });
```

### Results and failures

`frontend/js/bridge.js` wraps invoke in all three Tauri shapes and **catches
rejections, logs them, and resolves `null`**. Consequences:

- `null` means "failed or unavailable", not "empty success".
- `Result<(), String>` commands are effectively fire-and-forget from the UI:
  `Ok(())` also serializes as `null`. Commands whose success the UI must
  distinguish return `bool`, a count, or an object; this is why
  `library_add`, `playlist_rename`, `set_offline`, and friends return values.
- A rejected promise never reaches UI code unless the command returns an
  event or a value. Check the webview console for the logged error.
- Rust error strings are user-facing. They are written as complete sentences
  where the user can act on them (for example the offline message).

### Blocking work

Commands that touch the network or disk are `async` and move the blocking part
to `tauri::async_runtime::spawn_blocking`. Synchronous commands only read
in-memory state (library, queue, settings, cache counters) or perform a
bounded filesystem read.

## 2. Shared types

### `SongMeta`

Sent for every local/queued song and accepted by `play_song`, `add_to_queue`,
`play_next`.

```ts
{
  id: string;            // stable path hash for local, "yt:<id>" / "saavn:<id>" for online
  title: string;
  artist: string;
  album: string;
  duration_secs: number;
  file_path: string;
}
```

### `OnlineTrack`

Returned by search/browse/radio and accepted back by every online play,
enqueue, radio, and download command.

```ts
{
  video_id: string;
  title: string;
  artist: string;
  album?: string;
  duration_secs?: number | null;
  thumbnail?: string | null;
  cached?: boolean;
  source?: string;              // "" or "yt" = YouTube; "saavn" = JioSaavn
  artist_browse_id?: string | null;
}
```

The stable id is `source ? `${source}:${video_id}` : `yt:${video_id}``.
The backend constructs tracks in search, catalog, radio, and Saavn paths; the
frontend must preserve the object it receives.

### Catalog pages

```ts
SearchResultPage {
  tracks: OnlineTrack[];
  next_continuation?: string | null;
}

BrowsePage {
  title?: string;
  shelves: Shelf[];             // { title: string; items: ShelfItem[] }
  next_continuation?: string | null;
  header?: HeaderMeta | null;   // detail-page hero
}

ShelfItem {
  kind: "song" | "video" | "album" | "artist" | "playlist" | "unknown";
  title: string;
  subtitle?: string;
  thumbnail?: string | null;
  video_id?: string | null;     // playable rows
  browse_id?: string | null;    // detail-page target
  params?: string | null;       // opaque browse params
  playlist_id?: string | null;
}
```

### Other DTOs

| Type | Shape |
|---|---|
| `PlaybackStateDto` | `{status, position_secs, duration_secs, volume, muted, current_song}`; `status` is `Stopped`, `Playing`, `Paused`, etc. |
| `StatsDto` | `{total_plays, total_listened_secs, total_skips, streak_days, avg_listen_ratio, top_songs: TopSongDto[], top_artists: [name, plays][], daily_minutes: [day, minutes][]}` |
| `TopSongDto` | `{id, title, artist, plays}` |
| `LibraryEntry` (`CatalogEntry`) | `{id, kind, title, subtitle, thumbnail, browse_id, params, video_id, added_at}` |
| `PlaylistTrack` | `{source: "yt" \| "saavn" \| "local", id, title, artist, album, duration_secs, thumbnail, file_path?}` |
| `UserPlaylist` | `{id, name, created_at, modified_at, tracks: PlaylistTrack[]}` |
| `SmartPlaylist` | `{id, name, rules: FilterRule[], max_songs, sort_by, sort_ascending}` |
| `BatchJob` | `{id, label, tracks: [{video_id, title, state}], active}`; `state` is `pending`, `done`, or `failed:<reason>` |
| `LyricsDto` | `{synced, lines: [{time_secs, text}]}` |
| `EnhancedLyricsDto` | `{track_id, synced, source, has_word_sync, has_romanized, has_translation, lines: EnhancedLyricLineDto[]}` |
| `SyncedLyricsDto` | `{synced: [{time_secs, text}], plain: string \| null}` |

`EnhancedLyricLineDto` is `{time_secs, text, words: [{word, start_ms, end_ms}],
romanized, translation}`. Word timings are synthesized from the line span, not
provided by LRCLIB.

## 3. Command catalog

### Library (4)

| Command | Arguments | Returns | Notes |
|---|---|---|---|
| `get_library` | — | `SongMeta[]` | In-memory scan result |
| `start_scan` | — | `bool` | `false` when a scan is already running; progress via events |
| `get_cover` | `songId` | `string \| null` | Base64 `data:image/jpeg` URL from the cover cache |
| `search_library` | `query` | `SongMeta[]` | Case-insensitive title/artist/album substring |

### Playback (11)

| Command | Arguments | Returns | Notes |
|---|---|---|---|
| `play_song` | `song: SongMeta` | `null` | Pushes to the queue and plays it now; `previous()` works |
| `pause` | — | `null` | |
| `resume` | — | `null` | |
| `seek` | `positionSecs` | `null` | Seconds |
| `set_volume` | `volume` | `null` | Clamped 0–1 |
| `toggle_mute` | — | `null` | |
| `get_playback_state` | — | `PlaybackStateDto` | Poll fallback; events are the primary stream |
| `next` | — | `null` | Runs the full advance policy |
| `previous` | — | `null` | Restarts if position > 3 s, else pops history |
| `set_playback_modes` | `shuffle`, `repeat` | `null` | Shuffle 0/1/2 (2 = smart radio), repeat 0/1/2 |
| `set_autoplay` | `enabled` | `null` | Persists immediately |

`play_song` rejects when the engine is unavailable. `next` returns `Ok` even
when the advance policy stops (no queue, no autoplay); use events to observe
the result.

### Queue (7)

| Command | Arguments | Returns | Notes |
|---|---|---|---|
| `get_queue` | — | `SongMeta[]` | Upcoming + current, in queue order |
| `add_to_queue` | `song` | `null` | Appends |
| `play_next` | `song` | `null` | Inserts at the front |
| `remove_from_queue` | `songId` | `null` | Removes the first matching id |
| `clear_queue` | — | `null` | Clears upcoming and history |
| `queue_move` | `from`, `to` | `bool` | Indices into the **upcoming** list only |
| `queue_play_at` | `index` | `null` | Removes that upcoming row and plays it now |

Every mutation emits `queue-changed`.

### Settings and folder (4)

| Command | Arguments | Returns | Notes |
|---|---|---|---|
| `get_settings` | — | `AppSettings` | Full object from [data-storage.md](data-storage.md#2-settingsjson) |
| `save_settings` | `settings` | `null` | Persists, applies DSP/audio fields, then replaces in-memory state |
| `pick_folder` | — | `string \| null` | Native dialog; saves and starts a scan; `null` on cancel |
| `set_music_folder` | `path` | `string` | Saves and rescans; error when the scan cannot start |

### Recommendations and likes (4)

| Command | Arguments | Returns | Notes |
|---|---|---|---|
| `get_recommendations` | — | `SongMeta[]` | 6 picks anchored on the current song; deterministic fallback when the recommender is busy/unavailable |
| `recommended_for_home` | — | `{header, songs} \| null` | `null` hides the Home shelf; header is "Because you liked <artist>" |
| `set_song_like` | `songId`, `liked` | `null` | Backend is the source of truth for ♥ |
| `get_liked_ids` | — | `string[]` | Used for the one-time frontend likes migration and the Liked filter |

### Online catalog and search (6)

| Command | Arguments | Returns | Notes |
|---|---|---|---|
| `online_search` | `query`, `videos?`, `continuation?` | `SearchResultPage` | Saavn is merged into first-page Songs results only |
| `online_search_suggestions` | `query` | `string[]` | Empty on offline/error/short query; never rejects for those |
| `online_home` | — | `BrowsePage` | Cached/stale-while-revalidate |
| `online_browse` | `browseId`, `params?` | `BrowsePage` | Album/artist/playlist/genre detail |
| `online_genres` | — | `ShelfItem[]` | Mood/genre grid |
| `online_available` | — | `bool` | Desktop: yt-dlp present. Android: always `true` (pure-Rust resolvers) |

`online_search`, `online_home`, `online_browse`, and `online_genres` are
Airplane-Mode gated and return the offline error string.

### Online playback and radio (4)

| Command | Arguments | Returns | Notes |
|---|---|---|---|
| `online_play` | `track: OnlineTrack` | `null` | Resolves interactively, pushes to the front, plays; emits `online-status` |
| `online_enqueue` | `track` | `null` | Background resolve, appends to the queue |
| `online_play_next` | `track` | `null` | Background resolve, inserts at the front; emits `queued-next` |
| `online_start_radio` | `track` | `null` | Clears upcoming, plays the seed, then chains related tracks |

All four are offline-gated. A resolution failure emits
`online-status {stage:"error"}` (except the download path, which uses
`download-state`).

### Downloads, batches, offline, cache (9)

| Command | Arguments | Returns | Notes |
|---|---|---|---|
| `online_download` | `track` | `null` | Cache fill without touching the queue; emits `download-state` |
| `online_download_batch` | `label`, `tracks` | `string` | Persisted batch id; starts a worker unless offline |
| `online_batches` | — | `BatchJob[]` | All persisted jobs |
| `online_batch_retry` | `id` | `null` | Resets failed tracks to pending and restarts |
| `online_batch_cancel` | `id` | `null` | Cooperative: in-flight tracks finish |
| `set_offline` | `enabled` | `bool` | Persists; lifting resumes pending batches |
| `get_offline` | — | `bool` | |
| `online_cache_usage` | — | `{used_bytes, cap_mb}` | Includes `.part`/`.ytdl` sidecars |
| `set_online_cache_cap` | `mb: number \| null` | `{used_bytes, cap_mb}` | Persists, prunes immediately; env override still wins |

`online_clear_cache` is listed with lyrics below because it is a cache
command, not a batch command.

### Saved catalog library (4)

| Command | Arguments | Returns | Notes |
|---|---|---|---|
| `library_add` | `entry: LibraryEntry` | `bool` | Backend stamps `added_at`; same id replaces |
| `library_remove` | `kind`, `id` | `null` | `kind` is `album`, `artist`, or `track` |
| `library_list` | `kind` | `LibraryEntry[]` | Newest first |
| `library_contains` | `kind`, `ids` | `string[]` | Subset of ids that are saved |

These are online-catalog bookmarks, stored in `lib:album` / `lib:artist` /
`lib:track`. They are not the local file library (`get_library`).

### User playlists (7)

| Command | Arguments | Returns | Notes |
|---|---|---|---|
| `playlist_create` | `name` | `UserPlaylist` | 1–200 characters after trim |
| `playlist_list` | — | `UserPlaylist[]` | |
| `playlist_rename` | `id`, `name` | `bool` | |
| `playlist_delete` | `id` | `bool` | |
| `playlist_add_track` | `id`, `track: PlaylistTrack` | `number` | New track count; max 1000 |
| `playlist_remove_track` | `id`, `index` | `bool` | |
| `playlist_move_track` | `id`, `from`, `to` | `bool` | Remove then insert |

Duplicates are allowed. `source` must be `yt`, `saavn`, or `local`; local rows
must carry `file_path`.

### Related, lyrics, and stream cache (6)

| Command | Arguments | Returns | Notes |
|---|---|---|---|
| `online_related` | `videoId` | `OnlineTrack[]` | Suggested shelf; seed excluded, shuffled |
| `online_lyrics` | `videoId` | `string \| null` | Unsynced YouTube Music text |
| `online_synced_lyrics` | `artist`, `title`, `durationSecs?` | `SyncedLyricsDto \| null` | LRCLIB; `None` means fall back |
| `get_lyrics` | `songId` | `LyricsDto \| null` | Local sidecar only |
| `get_enhanced_lyrics` | `trackId`, `title?`, `artist?`, `durationSecs?` | `EnhancedLyricsDto \| null` | Unified ladder; local sidecar works offline |
| `online_clear_cache` | — | `number` | Bytes freed |

`online_lyrics` and `online_synced_lyrics` are offline-gated.
`get_enhanced_lyrics` checks the local sidecar first, then returns `null`
offline; it never rejects just because lyrics are missing.

### Stats and smart lists (2)

| Command | Arguments | Returns | Notes |
|---|---|---|---|
| `get_stats` | — | `StatsDto` | Empty object shape when the recommender is unavailable |
| `get_playlists` | — | `SmartPlaylist[]` | Reads the `playlists` tree; no v0.7.0 command writes it |

### DSP / Sonic Lab (12)

| Command | Arguments | Returns |
|---|---|---|
| `set_eq` | `enabled`, `gains: number[]` | `null` |
| `set_normalization` | `mode: "Off" \| "Track"` | `null` |
| `set_stem_mode` | `mode` | `{mode, spec: {mode, label}}` |
| `set_loudness_norm` | `enabled`, `targetLufs?` | `{enabled, targetLufs, label}` |
| `get_autoeq_profiles` | — | `{profiles, activeProfile, tubeWarmth}` |
| `apply_autoeq_profile` | `profileId?`, `tubeWarmth?` | `{activeProfile, tubeWarmth, eqEnabled}` |
| `get_prefetch_plan` | — | `{enabled, count, streamingQuality, downloadQuality, sampleRate, instantBufferKb, gaplessPlayback, tubeWarmth, items}` |
| `get_autodj_plan` | — | `{enabled, outgoingBpm, incomingBpm, stretchRatio, effectiveIncomingBpm, beatGridOffsetMs, crossfadeDurationSec, phraseAlignedBars, label}` |
| `get_taste_dna` | — | `{subprofileCount, weights, recommendations}` |
| `apply_taste_dna` | `config?` (ignored) | `{applied, recommendations}` |
| `build_sonic_bridge` | `startTrackId?`, `targetTrackId?`, `steps?` | JSON path plan |
| `get_daypart_capsule` | `hour?` | JSON daypart capsule |

`set_stem_mode` accepts `full`, `instrumental`/`karaoke`, and
`vocals`/`vocals_only`. `set_loudness_norm` clamps the target to −24…−9 LUFS.
`get_prefetch_plan` reports the current queue's prefetch plan; it does not
start downloads. `get_autodj_plan` returns descriptive metadata for the UI —
the engine's crossfade is configured through `save_settings`.

### Jam sessions (4)

| Command | Arguments | Returns | Notes |
|---|---|---|---|
| `create_jam_session` | `hostName?` | JSON session | Builds a local session from the queue/library |
| `join_jam_session` | `roomCode`, `userName?` | JSON session | Normalizes the code, stores it in-process |
| `vote_jam_track` | `roomCode?`, `trackId`, `delta?` | JSON session \| `null` | Re-sorts the shared queue by votes |
| `get_jam_session` | — | JSON session \| `null` | |

**These commands are a local simulation in v0.7.0.** There is no network
transport, discovery, or multi-device sync; `JAM_STATE` is an in-process
`Mutex`. Room codes and participant names are display data.

### Utility (2)

| Command | Arguments | Returns |
|---|---|---|
| `get_platform_capabilities` | — | `{platform, rollout, canPickLocalFolder, hasNativeAudio, streamResolvers}` |
| `execute_sonic_command` | `query` | JSON match list from the local library |

`get_platform_capabilities` reports `linux`, `macos`, `windows`, or `android`,
and lists the resolvers available on that platform. Android includes
`piped`, `invidious`, `saavn`, `innertube_direct`; desktop adds `yt_dlp`.

## 4. Events

Subscribe with:

```js
const off = Bridge.listen('queue-changed', (envelope) => {
  const songs = envelope.payload ?? envelope;
});
```

Tauri delivers `{event, payload}`; legacy handlers also accept the bare
payload. `Bridge.listen` returns an unsubscribe function. A missing
`core:event` permission makes registration reject; the bridge logs it and
returns a no-op, so events then silently never arrive — check the console.

| Event | Emitted by | Payload | Consumer |
|---|---|---|---|
| `playback-event` | `event_bridge.rs` every 50 ms | Tagged object, see below | `app.js`, `online.js`, shell |
| `queue-changed` | Queue commands, advance, online enqueue/play paths | `SongMeta[]` | Queue panel |
| `scan-progress` | `scanner.rs` | `{done, total, current}` | Library/settings status |
| `library-updated` | `scanner.rs` | `SongMeta[]` | Library refresh |
| `index-progress` | `scanner.rs` | `{done, total}` | Indexing status |
| `index-complete` | `scanner.rs` | `{indexed}` | Indexing status |
| `online-status` | Online commands | `{stage, videoId, title, message?}` | `online.js` |
| `download-state` | `online_download` | `{videoId, stage, title, message}` | `online.js` |
| `download-progress` | Batch worker | `{batchId, label, done, failed, total, state}` | Downloads UI |

### `playback-event` payloads

```ts
{ type: "Playing", song: SongMeta }
{ type: "Paused", position_secs: number }
{ type: "Resumed" }
{ type: "Stopped" }
{ type: "Seeked", position_secs: number }
{ type: "PositionUpdate", position_secs: number }
{ type: "SongEnded", song_id: string, listened_secs: number, duration_secs: number }
{ type: "BufferingStarted" }
{ type: "BufferingComplete" }
{ type: "Error", message: string }
{ type: "VolumeChanged", volume: number }
```

`PositionUpdate` is the only high-frequency event. Do not trigger a layout or
network request from it; update the progress display only.

### `online-status` stages

| Stage | Meaning |
|---|---|
| `resolving` | A resolve started (interactive or queued) |
| `playing` | The track resolved and playback started |
| `queued-next` | `online_play_next` finished and inserted the row |
| `radio` | `online_start_radio` is clearing the queue and starting the seed |
| `error` | Resolution failed; `message` carries the reason |

`download-state` stages are `resolving`, `done`, and `failed`; on `done` the
`message` field is `null`.

### `download-progress`

`state` is `running` while the batch worker is active and `settled` once it
has no pending work or was cancelled. `done` counts completed tracks, `failed`
counts rows whose state starts with `failed:`.

## 5. Offline gating

`ensure_online` is called by every network-reaching command. When Airplane
Mode is on, these commands reject with:

```text
Offline — turn Airplane Mode off to reach YouTube Music
```

Gated: `online_search`, `online_home`, `online_browse`, `online_genres`,
`online_play`, `online_enqueue`, `online_play_next`, `online_start_radio`,
`online_download`, `online_download_batch`, `online_related`,
`online_lyrics`, `online_synced_lyrics`, and the online branch of
`get_enhanced_lyrics`.

Not gated: local playback and queue commands, settings, library/playlist
commands, `online_cache_usage`, `set_online_cache_cap`, `online_clear_cache`,
`online_available`, `get_lyrics` (sidecar only), and `get_platform_capabilities`.

Special cases:

- `online_search_suggestions` returns `[]` instead of rejecting.
- `online_download_batch` persists the job and starts no worker; the tracks
  stay `pending` until the mode is lifted.
- `online_start_radio` checks Airplane Mode **before** clearing the upcoming
  queue, so an offline attempt leaves the queue untouched. Once online, it
  clears the queue and then calls `online_play`.

## 6. Adding a command

1. Implement the handler in `src-tauri/src/commands.rs`:

   ```rust
   #[tauri::command]
   pub fn my_command(state: State<AppState>, song_id: String) -> Result<bool, String> {
       // validate → do bounded work → return a value the UI can gate on
       Ok(true)
   }
   ```

2. Use `Result<T, String>` with user-readable errors. Use `async fn` plus
   `spawn_blocking` for network/disk work.
3. Register it in `src-tauri/src/lib.rs` inside `tauri::generate_handler!`.
   An unregistered command fails at runtime with "command not found".
4. Call it from JS with camelCase arguments.
5. Emit an event instead of blocking the UI for long work; include a
   `stage`/`type` discriminator and the ids the UI needs.
6. Add a test for validation and failure, not just the happy path.
7. Update this file, `ARCHITECTURE.md` if the system shape changed, and
   `CHANGELOG.md` if the command is user-visible.

Do not return `Result<(), String>` when the UI needs to distinguish success
from failure; Tauri serializes `Ok(())` and `Bridge` maps rejection to the same
`null`.

## 7. Where to go next

- [frontend.md](frontend.md) — bridge implementation, store, router.
- [online-stack.md](online-stack.md) — what the online commands do internally.
- [data-storage.md](data-storage.md) — where command writes land.
- [development.md](development.md#add-an-ipc-command) — step-by-step workflow.
