# Online stack

The online stack is everything between a webview request and a local audio
file: catalog search and browse, radio, lyrics, source resolution, download
verification, and the stream cache. It is independent of the local library
scanner and the recommender; the two meet only when an online track becomes a
`SongMeta` and enters the playback queue.

All modules live under `src-tauri/src/online/`. Every network path is bounded,
every download is atomically published, and every failure returns an honest
error instead of a partial file.

## 1. Module map

| Module | Owns |
|---|---|
| `client.rs` | InnerTube HTTP choke point: identity, version refresh, concurrency, retry/backoff, request coalescing |
| `models.rs` | IPC-facing types (`OnlineTrack`, `BrowsePage`, `SearchResultPage`, `ShelfItem`) |
| `parse.rs` | All JSON → model parsing; the only drift-sensitive file |
| `search.rs` | Search, continuation paging, typeahead, 5-minute result cache |
| `catalog.rs` | Home feed, browse pages, genres, stale-while-revalidate cache |
| `radio.rs` | Related tracks via the `next` endpoint |
| `saavn.rs` | JioSaavn search, cross-source matching, quality-tiered direct-URL resolution |
| `sources.rs` | Piped / Invidious / InnerTube Direct fast path |
| `stream.rs` | yt-dlp resolution, locks, disk cache, verification, pruning, cover art, polish |
| `download.rs` | Session-scoped single-download state machine |
| `lyrics.rs` | YouTube Music (InnerTube) unsynced lyrics |
| `lrclib.rs` | Synced LRC from LRCLIB |

Search and radio use YouTube Music's InnerTube API with the `WEB_REMIX` client
identity — the same technique used by Metrolist and InnerTune. The client
version is auto-refreshed from the music.youtube.com homepage with a 24-hour
TTL, so response drift is not tied to a frozen version stamp.

## 2. HTTP client policy

`online/client.rs` is the single choke point for catalog requests. Stream and
thumbnail downloads deliberately use separate clients because they are
throughput-oriented, not rate-limit-sensitive.

| Rule | Value |
|---|---|
| Client identity | `WEB_REMIX` with the public web key |
| Client version | Refreshed from the homepage, 24 h TTL, 5 min retry on failure |
| In-flight requests | At most 2 (`MAX_CONCURRENCY`) |
| Attempts | 2 |
| Backoff | 400 ms base ± 150 ms jitter |
| Request timeout | 20 s total, 10 s connect |
| Slow-request warning | 5 s |
| Identical in-flight requests | Coalesced onto one round-trip; waiters hold no throttle slot (60 s waiter bound) |

The version refresh is stale-while-revalidate: once any value is cached, a
request never waits on the homepage. A failed refresh keeps the previous value
and retries later; the compiled fallback is used only when no value has ever
been fetched.

## 3. Search and browse

### Search

`online_search` accepts `query`, `videos`, and an optional `continuation`.

- The Songs chip uses the `EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D` filter; Videos uses
  `EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D`.
- First pages are cached for **5 minutes** under a normalized key (case and
  whitespace collapse; the request text is unchanged). The cache holds **64**
  entries and clears wholesale when full.
- Continuation pages bypass the cache. The `next_continuation` token from the
  previous page is passed back verbatim.
- On the first page of a Songs search, JioSaavn results are appended as an
  independent block (**10** rows, `SEARCH_MERGE_LIMIT`). JioSaavn failures are
  contained inside `search_songs`, which returns `[]`; YouTube results can
  never be poisoned by the other source. Videos and continuation pages are
  pure YouTube.
- A parsed-zero-results page logs a warning because it usually means the
  response shape changed — it is not treated as a legitimate empty search.

`online_search_suggestions` is best-effort: fewer than 2 characters, offline
mode, a network error, or shape drift all return an empty list. The typeahead
dropdown stays closed; no error UI appears.

### Browse and home

`online_home`, `online_browse`, and `online_genres` share the catalog cache:

| Property | Value |
|---|---|
| Fresh for | 5 minutes |
| Stale-serveable for | Up to 6 hours after the TTL |
| Cache cap | 48 browse pages |
| Refresh policy | One background refresh per key; callers get the stale page immediately |
| Empty re-parse | Never overwrites a good cached page |

Home is a `BrowsePage` of shelves. `online_browse` takes the `browse_id` and
optional `params` captured from a shelf item, which is what makes album,
artist, playlist, and genre detail pages work. `online_genres` returns the
mood/genre grid.

### Radio

`online_related` and `online_start_radio` both use the InnerTube `next`
endpoint seeded by a video id. Results are shuffled, the seed is excluded, and
the list feeds three surfaces:

- the Suggested shelf in the expanded player;
- explicit "Start Radio" from a context menu;
- autoplay's queue-empty fallback for `yt:` tracks.

Online radio advance and warmup are gated by the persisted `autoplay` setting.
With autoplay off, a finished queue stops instead of fetching related tracks.
The next pick is warmed while the current track plays; the warmup is skipped
for very short listens so skip-churn does not spawn downloads.

## 4. Resolution ladder

`online/stream.rs::resolve_track` is the dispatcher. `OnlineTrack.source`
decides the path before any network work:

| `source` | Path |
|---|---|
| `""` or `"yt"` | YouTube ladder below |
| `"saavn"` | JioSaavn direct path |
| Anything else | Immediate error: `unknown online source '<value>'` |

The YouTube ladder, in order:

1. **Cache hit.** Exact `<video_id>.` prefix in the stream cache. Instant and
   independent of any extractor being installed.
2. **Spawn the cover job.** The thumbnail fetch runs while the audio resolves.
   A missing cover is cached as a miss by the frontend, so art must land before
   the `Playing` event.
3. **JioSaavn cross-source match.** Only when the title is non-empty. Search the
   first 4 Saavn results for `"<title> <artist>"`, then accept the first row
   where all of these hold:
   - one title contains the other (case-insensitive);
   - the artist is empty, or the Saavn artist contains any artist token of at
     least 2 characters;
   - both durations are known and differ by at most **4 seconds** (unknown
     durations pass).

   A match resolves through the Saavn path, normally in a few hundred
   milliseconds. There is no setting or per-track override to disable this
   heuristic.
4. **Direct-URL fast path.** Piped, Invidious, then InnerTube Direct, under a
   shared **10-second** wall-clock budget. No child process is spawned and
   `yt-dlp` need not be installed. Any failure falls through to yt-dlp.
5. **yt-dlp.** Interactive click-to-play runs **1 attempt with a 90-second
   total budget**, and the wait for a downloader slot counts against that
   budget. Background work (prefetch, radio warmup, batch) runs **2 attempts of
   120 seconds each**, with 600 ms between attempts. At most **3** yt-dlp
   children may exist process-wide.

Worst-case interactive latency is therefore about 10 s of fast path plus 90 s
of yt-dlp, unless the slot wait exhausts the budget first. If `yt-dlp` is not
installed **and** the direct sources failed, the error tells the user to install
it; the app does not silently stall.

### yt-dlp format selection

The format selector follows the download-quality setting, then the streaming
setting:

| Setting | Selector |
|---|---|
| `low` | `139/140/bestaudio[abr<=96]/bestaudio` |
| `normal` | `140/bestaudio[abr<=160]/bestaudio` |
| `high` / `lossless` | `141/140/bestaudio[ext=m4a]/bestaudio` |

`141` is the 256 kbps AAC rung when YouTube offers it; `140` is 128 kbps AAC.
`lossless` here means "best available from the source", not a lossless master.

### Verification and publishing

- yt-dlp writes to `<id>.<ext>`; the file is only trusted after it appears and
  is at least **10 KB** (`MIN_FILE_BYTES`). A smaller file is deleted and the
  attempt counts as failed.
- The fast path and Saavn both write a `.part` file, verify the size, then
  rename atomically. A killed process leaves a sidecar, never a fake cache hit.
- A successful resolve prunes the cache to the effective cap.
- Duration is taken from search metadata when present; otherwise it is probed
  with Symphonia. Cover art is fetched under the download and also warmed in
  `to_song_info`.

## 5. JioSaavn path

The `saavn.dev` wrapper named in the original issue is discontinued and its
domain no longer resolves. The module therefore talks to the native web API at
`https://www.jiosaavn.com/api.php` with `_format=json&_marker=0&api_version=4&ctx=web6dot0`.

### Search

`search.getResults` returns song rows. Each row is:

- dropped unless `type == "song"`;
- dropped unless the id matches `[A-Za-z0-9_-]{1,64}`;
- HTML-unescaped (`&amp;`, `&quot;`, numeric entities);
- given a 500×500 thumbnail by replacing `150x150` in the CDN URL;
- badged `cached` from the `saavn:{id}` cache namespace.

Every request has a **3-second** metadata timeout. `MUSICO_SAAVN_API` overrides
the API base for a proxy or mirror; a blank value means the default.

### Resolve

1. Cache hit on `saavn:{id}`.
2. Spawn the cover job.
3. `song.getDetails` for the id; take `more_info.encrypted_media_url`.
4. Base64-decode, DES-ECB decrypt with the public web key `38346591`, remove
   PKCS7 padding.
5. Enforce the allowlist: the plaintext must be an `https://aac.saavncdn.com/`
   URL containing `_96.`. Anything else is rejected before a fetch.
6. Build a quality ladder from the `320kbps` declaration and the active tier:

   | Tier | Order |
   |---|---|
   | `low` | 96 only |
   | `normal` | 160 → 96 |
   | `high` / `lossless` | 320 (if declared) → 160 → 96 |

   Live playback uses `streaming_quality`; background resolves (downloads and
   batches) use `download_quality`.
7. Fetch the whole body with the remaining budget, capped at **32 MB**.
8. Verify at least 10 KB, write `.part`, rename to `saavn_{id}.mp4` (the
   Windows-safe encoding), prune.

Budgets: **90 s** interactive, **240 s** background. Concurrent resolves for
the same id serialize on a per-id lock.

## 6. Direct-URL source pools

The fast path exists because YouTube rate-limits extraction per IP; yt-dlp can
hang while public instances still serve the same audio.

| Source | Default hosts | Notes |
|---|---|---|
| Piped | `pipedapi.ducks.party`, `pipedapi.private.coffee`, `pipedapi.kavin.rocks`, `pipedapi.adminforge.de`, `pipedapi.drgns.space`, `pipedapi.reallyaweso.me` | Prefers `audio/mp4` at or below 192 kbps |
| Invidious | `inv.nadeko.net`, `invidious.nerdvpn.de`, `yewtu.be`, `invidious.f5.si` | Uses only the `latest_version` proxy with `local=true`; googlevideo URLs are IP-locked and never used |
| InnerTube Direct | `www.youtube.com` | Pure-Rust `ANDROID_VR` extraction; essential on Android, where no child process exists |

Overrides are comma-separated host lists. An explicitly empty value disables
that kind, which is how hermetic tests keep the network out:

```sh
MUSICO_PIPED_INSTANCES=       # disable Piped
MUSICO_INVIDIOUS_INSTANCES=   # disable Invidious
MUSICO_INNERTUBE_DIRECT=      # disable InnerTube Direct
```

Instance order is not fixed: an EWMA health board tracks latency and success
rate, demotes an instance for 15 minutes after 2 consecutive failures, and
sorts healthy instances first. Per-instance failures log at `debug!`.

Fast-path bounds: 2 s connect, 5 s per metadata request, 64 MB source body,
2 MB metadata body, 10 s shared deadline checked per chunk. The `.part` file is
removed on any failure.

## 7. Stream cache

Location: `~/.cache/musico-online/online/` by default.

| Rule | Value |
|---|---|
| Default cap | 1 GB |
| Cap precedence | `MUSICO_ONLINE_CACHE_MB` > Storage slider (`online_cache_mb`) > 1 GB |
| Minimum cap | 64 MB |
| Prune trigger | Completed (non-sidecar) bytes above the cap; target is 80% of the cap (`CACHE_PRUNE_TARGET`) |
| Eviction order | Oldest mtime first |
| Sidecar sweep | `.part` and `.ytdl` files older than 24 h are deleted on prune; fresh sidecars are excluded from the LRU total |
| Usage accounting | The Storage UI counts every file in the directory, including sidecars |
| Filename encoding | Namespaced ids replace `:` with `_` (`saavn_<id>.mp4`); a raw legacy name is still readable |
| Lookup | Encoded `<id>.` prefix first, then the raw prefix; an empty id never matches |
| Index | In-memory `id → path`, re-validated against disk on every hit |

`MUSICO_CACHE_DIR` replaces the stream-cache root entirely. It is intended for
tests and live runs: covers and `library.json` still use the normal cache
directory, because `scanner.rs` resolves those through `dirs::cache_dir()`.

The cache is the persistent truth for "is this downloaded". The per-session
download map in `download.rs` only drives UI state (`resolving → done |
failed`); invoking the command again is the retry mechanism. Batch state is
persisted separately in sled.

## 8. Lyrics

`get_enhanced_lyrics` is the unified command for local and online songs. It
resolves metadata from the engine, then the library, then the queue, then the
caller's arguments, and tries sources in this order:

1. **Local sidecar `.lrc`** for local files (sidecar only; no network).
2. **LRCLIB exact match** by artist, title, and duration.
3. **LRCLIB search fallback** filtered to a ±2-second duration window; the
   nearest candidate wins.
4. **YouTube Music unsynced lyrics** for `yt:` tracks (InnerTube `next` →
   `MPLYt_` lyrics browse id → description shelf).

Any LRCLIB error degrades to "no match", never an error dialog. Airplane mode
returns `None` after step 1. Romanization and translation overlays are settings
(`lyrics_romanize`, `lyrics_translate`); an `.lrc` line may carry them inline as
`primary | [romanized] | (translation)`.

Word-by-word timing is synthesized by distributing a line's span across its
tokens; LRCLIB provides line timestamps, not per-word timestamps. This is
labelled as synthesized, not as source-provided word sync.

## 9. Offline behavior

Airplane Mode (`set_offline`) persists `offline_mode` and flips the atomic
checked by `ensure_online`. Online commands fail in under 10 ms with:

> Offline — turn Airplane Mode off to reach YouTube Music

What still works:

- local library, queue (for files on disk), playlists, likes, stats;
- cached stream files already on disk;
- local `.lrc` sidecars;
- cache usage, cap changes, and clearing the cache.

What changes:

- `online_search_suggestions` returns `[]` rather than an error.
- `online_download_batch` creates the persisted job with pending tracks and
  does not start a worker. Lifting Airplane Mode resumes every pending batch in
  one sequential worker; startup does the same for jobs left pending by a
  previous run.
- Health checks and speculative prefetch/radio work are skipped.

## 10. Failure modes

| Symptom | Cause | Behavior |
|---|---|---|
| "yt-dlp is not installed and the direct-URL sources failed" | No yt-dlp, no instance served the track | Honest error with install hint |
| Playback stalls ~10 s then fails | All fast-path instances dead or timed out | yt-dlp still runs; if it also fails, the last reason is returned |
| "resolve budget exhausted waiting for a free downloader" | 3 yt-dlp children already running | Interactive play fails honestly instead of pinning "resolving" |
| Search returns no rows but the network is up | InnerTube response shape changed | Parse is shelf-tolerant; a zero-result page logs a warning |
| Saavn rows missing | API shape drift, timeout, or region block | Contained to the Saavn block; YouTube results still render |
| Wrong take plays from a YouTube row | Cross-source heuristic matched a different Saavn recording | No per-track override exists; report the track ids |
| Truncated or tiny file in cache | Killed download or bad proxy | `.part` is removed or swept; a published file under 10 KB is deleted and retried |
| Cache keeps growing | Sidecars from killed runs | Prune sweeps sidecars older than 24 h and enforces the cap |
| Cover art missing | Thumbnail URL not on the allowlist, fetch failed, or frontend cached a miss | Restart the app or clear the cover file; playback is unaffected |

## 11. Budgets at a glance

| Operation | Bound |
|---|---|
| Search / browse metadata | 20 s request, 10 s connect, 2 attempts |
| InnerTube concurrency | 2 |
| Fast path | 10 s total |
| Saavn metadata | 3 s |
| Saavn download | 32 MB, 90 s interactive / 240 s background |
| yt-dlp interactive | 1 × 90 s including slot wait |
| yt-dlp background | 2 × 120 s |
| Concurrent yt-dlp children | 3 |
| Stream cache | 1 GB default, prune to 80%, floor 64 MB |
| Cover art | 5 MB body cap, 8 s request |
| Search cache | 5 min, 64 entries |
| Browse cache | 5 min fresh, +6 h stale, 48 entries |
| LRCLIB | 5 s request, ±2 s duration window |

## 12. Source etiquette and legal note

The project talks to public, unofficial endpoints. It does not bypass DRM and
does not host or proxy catalog content itself. Instance lists change; this
document records the defaults at v0.7.0, not a guarantee that they are alive
today. Use `MUSICO_PIPED_INSTANCES`, `MUSICO_INVIDIOUS_INSTANCES`,
`MUSICO_INNERTUBE_DIRECT`, and `MUSICO_SAAVN_API` to point at instances you
trust. Respect local copyright law and the terms of the services you use.

## 13. Where to go next

- [data-storage.md](data-storage.md) — cache lifecycle, sled trees, settings.
- [ipc.md](ipc.md) — the online commands and event payloads.
- [testing.md](testing.md#live-network-tests) — the live gates for this stack.
- [troubleshooting.md](troubleshooting.md) — diagnosing source failures.
- [ARCHITECTURE.md](../ARCHITECTURE.md) — how online playback fits the app.
