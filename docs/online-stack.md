# Online stack

The online stack is the bridge between a UI request (`online.*`, `track.resolve`, `lyrics.enhanced`, `library.playlist.import`) and playable audio or catalog metadata. All modules live under [`musico_core/src/online/`](../musico_core/src/online/mod.rs), shared identically by Android and Desktop CMP builds and integrated with `musico_recommender` in [`musico_core/src/mobile.rs`](../musico_core/src/mobile.rs).

---

## 1. Module map

| File | Concern |
|---|---|
| `mod.rs` | Re-exports, `fetch_track_metadata`, and 12 cross-module `live_*` tests |
| `client.rs` | Shared HTTP client, InnerTube `WEB_REMIX` identity, auto-refreshing `clientVersion`, RAII concurrency cap (`2`), 1 retry |
| `models.rs` | Wire structs (`OnlineTrack`, `BrowsePage`, `SearchResultPage`, `ShelfItem`, `song_id()`) |
| `parse.rs` | All InnerTube JSON shape parsing (the only drift-sensitive file) |
| `catalog.rs` | Home feed (`SWR` cache), Moods & Genres, Browse + continuations |
| `search.rs` | InnerTube search + JioSaavn merge + autocomplete suggestions + 5-min TTL cache |
| `radio.rs` | `RD<videoId>` radio via InnerTube `next`, avoiding the last `12` played tracks |
| `saavn.rs` | JioSaavn native `api.php`, DES-ECB URL decryption, `saavn:` cache stem, cross-source matching & streaming |
| `sources.rs` | `InnerTubeDirectSource` (first) + EWMA-ranked `Piped` & `Invidious` pools, hedged stream opening, `STREAM_LOUDNESS` capture |
| `stream.rs` | Cache index/LRU pruning, `resolve_track`, `resolve_track_streaming`, Desktop `yt-dlp -g` / `run_yt_dlp`, `.meta.json` sidecars |
| `stream_server.rs` | Loopback HTTP/1.1 range server (`127.0.0.1:0`, 32-hex `OsRng` tokens, `16 KiB` startup gate) |
| `playlist_import.rs` | Zero-OAuth CSV/JSON/M3U parser (`4 MiB` / `500` tracks) + HTTPS allowlisted Spotify/Apple Music URL importer |
| `lrclib.rs` | LRCLIB.net synced & plain lyrics with duration matching |
| `lyrics.rs` | InnerTube `next` → `MPLYt_` → `browse` plain lyrics fallback |
| `download.rs` | Legacy session download stage tracker (unused by `mobile.rs` dispatcher) |

---

## 2. InnerTube (`client.rs`, `catalog.rs`, `search.rs`, `radio.rs`, `parse.rs`)

- **Client identity:** Emulates YouTube Music's `WEB_REMIX` web client. `clientVersion` is scraped from `music.youtube.com` on first use (24-hour TTL, fallback stamped version if offline/blocked) and invalidated immediately on HTTP `400` / `403`.
- **Concurrency & retry:** A semaphore caps simultaneous InnerTube requests at **2**. Transient `5xx` or network errors retry once after `250 ms`.
- **Search (`search.rs`):** Normalizes queries, keeps a 5-minute in-memory TTL cache (64 entries), and merges up to `SEARCH_MERGE_LIMIT = 10` JioSaavn tracks after InnerTube results.
- **Radio (`radio.rs`):** Calls `next` with `RD<videoId>` and filters out the seed track plus the last `12` played tracks (`RADIO_REMEMBER = 12`).

---

## 3. Progressive stream resolution (`stream.rs`, `stream_server.rs`, `sources.rs`, `saavn.rs`)

See [ADR-0003](adr/0003-tiered-stream-resolution.md) and [ADR-0006](adr/0006-loopback-progressive-streaming.md) for the full design rationale.

### Dispatch (`OnlineTrack.source`)
- `""` or `"yt"` → YouTube resolution path.
- `"saavn"` → JioSaavn direct path (`saavn:<id>`).
- Anything else → immediate error (never falls through to `yt-dlp`).

### Interactive streaming path (`resolve_track_streaming`, `allowStream = true`)
1. **Local cache check:** Checks `cached_file(song_id)` and `cached_file(video_id)`. A hit returns the disk path immediately (works in Airplane Mode) and writes `{stem}.meta.json` if missing.
2. **Spawn async cover fetch:** `CoverJob::spawn` runs without blocking playback start.
3. **Parallel race (YouTube tracks with a title):**
   - **Branch A — JioSaavn cross-source (`saavn.rs`):** Searches `6` results (`CROSS_SOURCE_SEARCH_TIMEOUT = 2,500 ms`) across primary and romanized `" - "` tail queries, normalizes titles alphanumerically, checks artist word overlap, disqualifies `> 6 s` AND `> 20%` relative duration drift, ranks by minimum duration drift, and decrypts `encrypted_media_url` (DES-ECB key `"38346591"`).
   - **Branch B — Direct-URL hedged fast path (`sources.rs`):** Tries `InnerTubeDirectSource` **first**, followed by EWMA-sorted `Piped` (`6` defaults) and `Invidious` (`4` defaults) pools under a `10 s` `FAST_PATH_BUDGET`. Uses `try_sources_open_stream_hedged` (`STREAM_HEDGE_DELAY = 900 ms`, `STREAM_HEDGE_BATCH = 2`, `METADATA_TIMEOUT = 3,500 ms`, `STREAM_READ_TIMEOUT = 15 s`).
4. **Loopback HTTP/1.1 range stream (`stream_server.rs`):** Whichever branch opens first hands its reader to `LoopbackStreamServer`, which pumps bytes into `{stem}.{seq}.part`, waits up to `6 s` for `INITIAL_READY_BYTES = 16 KiB`, and returns `http://127.0.0.1:<port>/stream/<32-hex-OsRng-token>`. Both Android Media3 and Desktop `LoopbackHttpMediaSource` play directly from this URL while the download completes and atomically renames `.part` → final cache file.
5. **Desktop `yt-dlp` fallback (non-Android only):** If both race branches miss, Desktop checks `yt-dlp_available()`, tries `yt-dlp -g` progressive streaming (`open_yt_dlp_stream`) through the same loopback server, and falls back to whole-file `run_yt_dlp` (`90 s` interactive / `2 × 120 s` background, global semaphore cap `3`). Android has no `yt-dlp` child process.

### `InnerTubeDirectSource` details (`sources.rs`)
- **No `ANDROID_VR`:** `ANDROID_VR` was removed (YouTube returns `403` after `512 KiB`).
- **Multi-client rotation:** Posts to `https://music.youtube.com/youtubei/v1/player?prettyPrint=false` rotating `VISIONOS_0_1`, `VISIONOS_1_02`, and `WEB_REMIX`, bootstrapping `visitorData`, filtering `n`-param URLs, remembering the last working client, and demoting failing profiles for `10` minutes.
- **Bitrate & loudness:** Default fast-path cap is `192_000` bps; `high` / `lossless` quality settings unlock `256_000` bps (`HIGH_TIER_AUDIO_BITRATE`). Captures `playerConfig.audioConfig` loudness into `STREAM_LOUDNESS` (up to `4096` entries).

### Background download path (`resolve_track`, `allowStream = false`)
Pre-warming and offline downloads run the same cache check and parallel JioSaavn-vs-Direct / `yt-dlp` ladder without `stream_server.rs`, writing `{stem}.{seq}.part` → atomic rename + `.meta.json` sidecar + LRU pruning (`1 GiB` default cap, `80%` prune target, `MIN_FILE_BYTES = 10 KiB`, `MAX_SOURCE_BYTES = 64 MiB` direct / `32 MiB` Saavn, `24 h` `.part`/`.ytdl`/`.tmp` sweep).

---

## 4. Playlist import (`playlist_import.rs`)

- **File import (`mode = "file"`):** Parses Exportify/TuneMyMusic/Soundiiz CSV, Spotify/Music0 JSON, or M3U/M3U8 (hard capped at `4 MiB` and `500` tracks).
- **Public URL import (`mode = "url"`):** Strict HTTPS + exact host allowlist (`open.spotify.com` embed `__NEXT_DATA__`, `music.apple.com` `application/ld+json` `MusicPlaylist`), redirect-validated, `5 s` connect / `10 s` total timeout, `4 MiB` body cap.
- **Matching:** Resolves tracks sequentially against `online.search` with `200 ms` pacing, cancelable via `library.playlist.import.cancel`.

---

## 5. Lyrics (`lrclib.rs`, `lyrics.rs`, `musico_core/src/lyrics.rs`)

`lyrics.enhanced` (gated by `ensure_online()`) checks:
1. **LRCLIB (`lrclib.rs`):** Queries `lrclib.net` with artist, title, and optional duration (`±2 s` tolerance when duration is known; filters durationless tracks cleanly). Synced LRC lines are passed to `lyrics::build_enhanced_lines_from_timed` for word-sync / romanization / translation DTOs; plain lyrics are returned as unsynced lines.
2. **YouTube Music (`lyrics.rs`):** If LRCLIB misses and `videoId` is present, walks `next` → `MPLYt_` → `browse` for plain lyrics.

---

## 6. Environment overrides & timeouts summary

| Env Var | Default | Effect |
|---|---|---|
| `MUSICO_PIPED_INSTANCES` | 6 default Piped hosts | Comma-separated override; blank `""` disables Piped |
| `MUSICO_INVIDIOUS_INSTANCES` | 4 default Invidious hosts | Comma-separated override; blank `""` disables Invidious |
| `MUSICO_INNERTUBE_DIRECT` | Enabled | Blank `""` disables `InnerTubeDirectSource` (hermetic tests) |
| `MUSICO_SAAVN_API` | `https://www.jiosaavn.com/api.php` | Overrides JioSaavn endpoint; blank `""` falls back to default |
| `MUSICO_ONLINE_CACHE_MB` | `1024` (over `settings.json`) | Stream cache cap in MiB |

| Operation | Budget / Timeout |
|---|---|
| Piped / Invidious / InnerTube Direct metadata | `3,500 ms` (`2 s` connect) |
| Fast-path hedged stream opening | `10 s` total (`900 ms` hedge delay, batch `2`, `15 s` read idle) |
| Loopback startup gate (`INITIAL_READY_BYTES`) | `16 KiB` within `6 s` |
| JioSaavn cross-source search / full API | `2,500 ms` / `6 s` |
| `yt-dlp` interactive / background | `90 s` (1 attempt) / `2 × 120 s` |
| Playlist URL import | `10 s` (`5 s` connect) |
