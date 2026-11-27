# JNI & IPC Reference

The production **Android** and **Desktop** Compose Multiplatform apps communicate with the pure-Rust core (`musico_core` → `libmusico_online_lib`) exclusively through the JNI dispatcher in [`musico_core/src/mobile.rs`](../musico_core/src/mobile.rs), mirrored by [`MusicoCore.kt`](../android/shared/src/commonMain/kotlin/io/github/aryan007h/musicoonline/core/MusicoCore.kt).

> **Web Preview Note:** The legacy `frontend/` + `server.ts` browser demo still uses [`frontend/js/bridge.js`](../frontend/js/bridge.js) (`Bridge.invoke()` over HTTP `/api/invoke/:cmd`). That mock layer is not part of any Desktop or Android build; see [Appendix A](#appendix-a-web-preview-bridge) below.

---

## 1. JNI Transport & Envelope

`musico_core/src/mobile.rs` exports exactly two `extern "system"` JNI functions on package `io.github.aryan007h.musicoonline.core.MusicoCore`:

1. **`initNative(filesDir: String, cacheDir: String)`**
   - Must be called once before any `invoke` call.
   - Creates `filesDir` and `cacheDir`, exports `HOME`, `XDG_DATA_HOME`, `XDG_CONFIG_HOME` = `filesDir` and `XDG_CACHE_HOME` = `cacheDir`, initializes logging (`android_logger` tag `"musico-native"` on Android; `env_logger` to stderr on Desktop), applies the persisted `online_cache_mb` cap, warms `MusicRecommender`, `stream_server`, `sources`/`saavn` HTTP clients, and (on Desktop) `PlaybackEngine`.
2. **`invoke(cmd: String, argsJson: String): String`**
   - Panic-safe (`catch_unwind`); never lets a Rust panic cross JNI.
   - Always runs on Kotlin `Dispatchers.IO` via `MusicoCore.call(cmd, args)` / `MusicoCore.callElement(cmd, args)`.
   - Reply JSON envelope:
     ```json
     { "ok": true, "data": { ... } }
     { "ok": false, "error": "human-readable message" }
     ```
   - There are **no Rust-to-UI push events** in CMP. Long-running progress (`library.playlist.import`) and Desktop playback state (`player.state`) are polled from Kotlin (`150 ms` loop in `DesktopPlayback.kt`).

### `OnlineTrack` JSON shape (camelCase / snake_case tolerant)

Commands taking a `track` (`track.resolve`, `playlist.add_track`, `library.likes.set`, `rec.on_song_changed`, `rec.rerank_candidates`) accept both `camelCase` and `snake_case` keys via `online_track_from_args`:

- `videoId` / `video_id` (required, 1–64 chars, `[A-Za-z0-9_-]`)
- `title`, `artist`, `album`, `source` (`""` | `"yt"` | `"saavn"`)
- `durationSecs` / `duration_secs` (optional `u32`)
- `thumbnail` (optional `string`)
- `artistBrowseId` / `artist_browse_id` (optional `string`)

---

## 2. Production JNI Command Catalog (48 Commands)

### Core, Settings & Updates (4)

| Command | Request Args | Response `data` | Notes |
|---|---|---|---|
| `ping` | `{}` | `{ version, core: "rust-mobile", initialized }` | `version` uses `MUSICO_VERSION_OVERRIDE` or `<CARGO_PKG_VERSION>-cmp`. |
| `settings.get` | `{}` | Full `AppSettings` object | Loads `<config_dir>/settings.json` with serde defaults. |
| `settings.save` | `{ settings: { ...partial } }` | Merged `AppSettings` object | Mutex-serialized read-merge-write; live-updates cache cap. |
| `app.update.check` | `{}` | `{ current, latest, updateAvailable, assetUrl, assetName, assetSizeBytes, assetDigest, notes }` | Queries GitHub `/releases/latest`; picks platform asset (`.apk` / `.dmg` / `.msi` / `.deb` / `.tar.gz`). |

### Online Catalog, Search & Stream Resolution (7)

All catalog/search commands require `offline_mode == false` (`ensure_online()`). `track.resolve` checks the local disk cache **before** `ensure_online()`, so cached tracks play offline.

| Command | Request Args | Response `data` | Notes |
|---|---|---|---|
| `online.search` | `{ query, videos?: bool, continuation?: string }` | `SearchResultPage` | Merges InnerTube + JioSaavn (`SEARCH_MERGE_LIMIT = 10`); 5-min TTL cache. |
| `online.home` | `{}` | `BrowsePage` (shelves + continuation) | SWR-cached InnerTube Home feed. |
| `online.genres` | `{}` | `{ items: ShelfItem[] }` | Moods & Genres (`FEmusic_moods_and_genres`). |
| `online.browse` | `{ browseId, params? }` or `{ continuation }` | `BrowsePage` | Album, artist, playlist, or genre detail / continuation. |
| `online.related` | `{ videoId }` | `{ tracks: OnlineTrack[] }` | InnerTube `RD<videoId>` radio, avoiding last 12 played tracks. |
| `online.suggestions` | `{ query }` | `{ suggestions: string[] }` | Autocomplete suggestions (requires online). |
| `track.resolve` | `OnlineTrack` + `{ background?: bool, allowStream?: bool }` | `{ path, songId, mode, resolveMs }` | `mode` is `"cached"`, `"stream"` (`http://127.0.0.1:<port>/stream/<token>`), `"file"`, or `"bg_file"`. Enqueues background acoustic indexing. |

### Cache Management (2)

| Command | Request Args | Response `data` | Notes |
|---|---|---|---|
| `online.cache.usage` | `{}` | `{ bytes: u64, capMb: u32? }` | Counts stream cache files + `.part`/`.ytdl`/`.tmp`/`.meta.json` usage. |
| `online.cache.clear` | `{}` | `{ freedBytes: u64 }` | Deletes cached audio and sidecars in `<cache_dir>/online/`. |

### User Playlists (7)

Backed by `sled` tree `lib:playlists` in `musico_recommender`.

| Command | Request Args | Response `data` |
|---|---|---|
| `playlist.list` | `{}` | `{ playlists: UserPlaylist[] }` |
| `playlist.create` | `{ name }` | Created `UserPlaylist` |
| `playlist.rename` | `{ id, name }` | `{ renamed: true }` |
| `playlist.delete` | `{ id }` | `{ deleted: true }` |
| `playlist.add_track` | `{ id, track: OnlineTrack }` | `{ count: usize }` (also saves `mobile:track_meta`) |
| `playlist.remove_track` | `{ id, index: usize }` | `{ removed: true }` |
| `playlist.move_track` | `{ id, from: usize, to: usize }` | `{ moved: true }` |

### Playlist Import (3)

Single-flight, cancelable importer (`musico_core/src/online/playlist_import.rs`).

| Command | Request Args | Response `data` | Notes |
|---|---|---|---|
| `library.playlist.import` | `{ mode: "url", url }` or `{ mode: "file", fileName?, content }` | `{ playlistId, name, total, matched, unmatched: [{ title, artist }], cancelled }` | `url` allows only `https://open.spotify.com/...` and `https://music.apple.com/...`. `file` parses CSV, JSON, or M3U/M3U8 (max `4 MiB` / `500` tracks). `200 ms` search pacing. |
| `library.playlist.import.status` | `{}` | `{ active, name, current, total }` | Polled by UI during import. |
| `library.playlist.import.cancel` | `{}` | `{ cancelling: true }` | Sets atomic cancel flag checked between tracks. |

### Library, Likes, Downloads & Migration (8)

| Command | Request Args | Response `data` | Notes |
|---|---|---|---|
| `library.put` | `{ kind, id, title, subtitle?, thumbnail?, browseId?, params?, videoId? }` | `{ saved: true }` | Saves bincode `CatalogEntry` (`kind`: `"album"`, `"artist"`, or `"track"`). |
| `library.remove` | `{ kind, id }` | `{ removed: true }` | Removes `CatalogEntry`. |
| `library.list` | `{ kind }` | `{ entries: CatalogEntry[] }` | Lists saved entries by kind. |
| `library.likes.set` | `{ track: OnlineTrack, liked?: bool }` | `{ liked: bool }` | Syncs `likes`, `lib:track`, and `mobile:track_meta` together. |
| `library.likes.list` | `{}` | `{ tracks: OnlineTrack[], ids: string[] }` | Reconstructs full `OnlineTrack` with live `cached` flag. |
| `library.downloads.list` | `{}` | `{ downloads: [{ track, sizeBytes, filePath }], totalBytes }` | Works offline; enriches stems via `mobile:track_meta` → `.meta.json` sidecar → playlists → offline fallback. |
| `library.downloads.enrich` | `{}` | `{ enriched, remaining }` | Online-only; fetches metadata for up to `15` un-enriched cached files (`250 ms` pacing) and tracks attempts in `mobile:enrich_attempted`. |
| `library.migrate_legacy` | `{ likedTracks?: OnlineTrack[], recentTracks?: OnlineTrack[] }` | `{ migratedLikes: usize }` | Idempotent one-time migration from legacy Android `SharedPreferences`. |

### Local Recommender & Smart Mixes (6)

| Command | Request Args | Response `data` | Notes |
|---|---|---|---|
| `rec.on_song_changed` | `{ videoId?, track? }` | `{ recorded: true }` | Updates session history + `mobile:track_meta`. |
| `rec.on_song_ended` | `{ videoId?, artist?, listenedSecs, durationSecs }` | `{ recorded: true }` | Records play/skip event; calls `bump_artist_listen` if acoustic indexing hasn't completed yet. |
| `rec.get_recommendations` | `{ currentVideoId?, topN?: 1..50 }` | `{ recommendations: [{ track, score, signal }] }` | Queries 8-signal local recommender over acoustically indexed tracks. |
| `rec.rerank_candidates` | `{ currentVideoId?, topN?: 1..60, candidates: OnlineTrack[] }` | `{ tracks: OnlineTrack[] }` | Re-ranks online candidates using rank decay + artist affinity + likes/dislikes + cache + recency + MMR diversity. |
| `rec.smart_mixes` | `{ localHour?: u32 }` | `{ mixes: [{ id, title, subtitle, accent, tracks }], topArtists: string[] }` | Generates Daily Discovery, On Repeat, Daypart Vibe, and Offline Ready mixes. |
| `rec.stats` | `{}` | `{ totalPlays, totalListenedSecs, totalSkips, streakDays, avgListenRatio, topArtists, exploration }` | Powers Profile / Taste DNA view. |

### Lyrics (1)

| Command | Request Args | Response `data` | Notes |
|---|---|---|---|
| `lyrics.enhanced` | `{ title, artist?, trackId?, videoId?, durationSecs? }` | `{ track_id, synced, source, has_word_sync, has_romanized, has_translation, lines }` | `ensure_online()`-gated; tries LRCLIB (synced → plain) then YouTube Music plain lyrics. |

### Desktop JVM Audio Engine (`player.*`, 10)

Compiled on `cfg(not(target_os = "android"))`. On Android these return `"player.* is desktop-only; Android uses Media3"`.

| Command | Request Args | Response `data` | Notes |
|---|---|---|---|
| `player.play_file` | `{ path \| filePath, id?, title?, artist?, album?, durationSecs? }` | `{ playing: true }` | Accepts local file paths OR `http://127.0.0.1:<port>/stream/<token>` via `LoopbackHttpMediaSource`. |
| `player.preload_next` | Same as `player.play_file` | `{ preloaded: true }` | Gapless next-track preloader. |
| `player.toggle` | `{}` | `{ playing: bool }` | Toggles pause/resume based on current engine state. |
| `player.pause` | `{}` | `{ ok: true }` | Pauses playback. |
| `player.resume` | `{}` | `{ ok: true }` | Resumes playback. |
| `player.stop` | `{}` | `{ ok: true }` | Stops playback and clears current song. |
| `player.seek` | `{ positionMs: f64 }` | `{ ok: true }` | Seeks in seconds (`positionMs / 1000.0`). |
| `player.set_volume` | `{ volume: f32 }` | `{ ok: true }` | Clamped to `0.0..=1.0`. |
| `player.set_eq` | `{ enabled: bool, gainsDb: f32[10] }` | `{ ok: true }` | 10-band ISO EQ gains clamped to `-12.0..=12.0` dB. |
| `player.state` | `{}` | `{ status, playing, buffering, positionMs, durationMs, volume, muted, ended, endedSongId, currentSongId, error }` | Polled every `150 ms` by `DesktopPlayback.kt`; drains `SongEnded` / `Error` events. |

---

## Appendix A: Web Preview Bridge

The unreleased browser/PWA preview (`npm start` → `server.ts` + `frontend/`) does **not** use `musico_core`. Instead, [`frontend/js/bridge.js`](../frontend/js/bridge.js) implements an 83-case mock/HTTP dispatcher (`Bridge.invoke`) talking to Express routes in [`server.ts`](../server.ts):

- `POST /api/invoke/:cmd` (plus `POST /api/invoke/cross_source_check`)
- `GET /api/audio/:trackId` (streams demo audio for browser preview playback)

See [docs/frontend.md](frontend.md) for the web preview stack.
