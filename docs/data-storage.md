# Data & storage

Music0 stores all state locally with no cloud account and no remote sync. Storage is split between the **pure-Rust core** (`musico_core` + `musico_recommender`) and **Compose UI platform prefs** (`PlatformPrefs`).

---

## 1. Directory layout (and path resolution)

All Rust paths derive from `DATA_SUBDIR = "musico-online"` in [`musico_core/src/identity.rs`](../musico_core/src/identity.rs) ([ADR-0002](adr/0002-separate-app-identity.md)).

### Important current nesting behavior (`v1.0.1`)

When Compose Multiplatform starts:
1. Kotlin resolves platform directories **with** `"musico-online"` already appended on Desktop ([`DesktopPlatform.kt`](../android/shared/src/desktopMain/kotlin/io/github/aryan007h/musicoonline/platform/DesktopPlatform.kt)) or app-private `filesDir` / `cacheDir` on Android (`MainActivity.kt`), and calls `MusicoCore.init(filesDir, cacheDir)`.
2. [`musico_core/src/mobile.rs`](../musico_core/src/mobile.rs) exports `HOME`, `XDG_DATA_HOME`, `XDG_CONFIG_HOME` = `filesDir` and `XDG_CACHE_HOME` = `cacheDir`.
3. `identity.rs` calls `dirs::{data_dir,config_dir,cache_dir}().join("musico-online")`.

As a result (given `dirs` 5.0.1's per-OS resolution: Linux/Android read `XDG_*`, macOS reads `$HOME`, and Windows calls Win32 `SHGetKnownFolderPath` ignoring `HOME`/`XDG_*`):

| Platform | UI Prefs / Posters (Kotlin) | Rust `db/` & `settings.json` | Rust Stream & Cover Caches |
|---|---|---|---|
| **Linux (native)** | `~/.local/share/musico-online/ui_prefs.json` · Posters: `~/Pictures/Music0/` | `~/.local/share/musico-online/musico-online/{db/,settings.json}` | `~/.cache/musico-online/musico-online/{online/,covers/}` |
| **Linux (Flatpak)** | `~/.var/app/io.github.aryan007h.Music0/data/musico-online/ui_prefs.json` | `~/.var/app/io.github.aryan007h.Music0/data/musico-online/musico-online/{db/,settings.json}` | `~/.var/app/io.github.aryan007h.Music0/cache/musico-online/musico-online/{online/,covers/}` |
| **macOS** | `~/Library/Application Support/musico-online/ui_prefs.json` · Posters: `~/Pictures/Music0/` | `~/Library/Application Support/musico-online/Library/Application Support/musico-online/{db/,settings.json}` | `~/Library/Application Support/musico-online/Library/Caches/musico-online/{online/,covers/}` |
| **Windows** | `%APPDATA%\musico-online\ui_prefs.json` · Posters: `%USERPROFILE%\Pictures\Music0\` | `%APPDATA%\musico-online\{db\,settings.json}` | `%LOCALAPPDATA%\musico-online\{online\,covers\}` |
| **Android** | `SharedPreferences("musico_ui")` · Posters: `<cacheDir>/lyric_cards/` | `<filesDir>/musico-online/{db/,settings.json}` | `<cacheDir>/musico-online/{online/,covers/}` |

> Note: `musico.log` and `library.json` no longer exist in `v1.0.x` (removed with Tauri / `scanner.rs`). Desktop logs go to `stderr` via `env_logger`; Android logs go to Logcat tag `musico-native`.

---

## 2. `settings.json` (`musico_core/src/config.rs`)

Written pretty-printed on `settings.save`. Every field has a `#[serde(default)]` so older or partial files remain valid.

| Key | Type | Default | Meaning |
|---|---|---|---|
| `music_folder` | `string \| null` | `null` | Legacy local-folder field retained for config compatibility |
| `volume` | `f32` | `0.68` | Output volume (`0.0`–`1.0`) |
| `palette` | `string` | `"nebula"` | Core setting default (CMP UI stores its 5 palettes in `PlatformPrefs`) |
| `art_tint` | `bool` | `true` | Dynamic artwork tinting |
| `smart_recs` | `bool` | `true` | Recommender / autoplay smart suggestions |
| `eq_enabled` | `bool` | `false` | 10-band EQ / AudioFX enabled |
| `eq_preset` | `string` | `"Flat"` | Active EQ preset name |
| `normalization` | `string` | `"Off"` | Normalization mode |
| `crossfade_enabled` | `bool` | `false` | Crossfade toggle |
| `crossfade_duration` | `f32` | `3.0` | Crossfade length in seconds |
| `library_view_mode` | `string` | `"list"` | Library layout preference |
| `shuffle_mode` | `u8` | `0` | `0` off · `1` shuffle · `2` smart radio |
| `repeat_mode` | `u8` | `0` | `0` off · `1` repeat one · `2` repeat all |
| `theme` | `string` | `"light"` | Core setting field (`"light"` \| `"dark"`) |
| `skin` | `string` | `"ios"` | Core/web-preview skin (`"ios"` \| `"m3e"`) |
| `autoplay` | `bool` | `true` | Queue continuation via radio picks |
| `online_cache_mb` | `u32 \| null` | `null` | Stream cache cap in MiB (`null` = `1024`; `MUSICO_ONLINE_CACHE_MB` overrides) |
| `offline_mode` | `bool` | `false` | Airplane mode: `ensure_online()` commands fast-fail; cached tracks play |
| `streaming_quality` | `string` | `"high"` | `"low"` \| `"normal"` \| `"high"` \| `"lossless"` (`high`/`lossless` unlock 256k fast path) |
| `download_quality` | `string` | `"high"` | `"normal"` \| `"high"` \| `"lossless"` |
| `audio_sample_rate` | `u32` | `48000` | Output sample rate (`44100` \| `48000` \| `96000`) |
| `gapless_playback` | `bool` | `true` | Preload next track for gapless transition |
| `predictive_prefetch` | `bool` | `true` | Background pre-resolution of upcoming tracks |
| `prefetch_count` | `u8` | `3` | Upcoming tracks to prefetch (`1`–`5`) |
| `instant_play_buffer_kb` | `u32` | `64` | Desktop instant-play prefill buffer in KiB |
| `autodj_beat_sync` | `bool` | `true` | Beat-aligned Auto-DJ preference |
| `stem_mode` | `string` | `"full"` | `"full"` \| `"instrumental"` \| `"vocals"` |
| `loudness_norm_enabled` | `bool` | `true` | Loudness normalization toggle |
| `target_lufs` | `f32` | `-14.0` | Target loudness (`-14.0`, `-16.0`, `-11.0`) |
| `autoeq_profile` | `string` | `"flat_studio"` | Headphone AutoEQ profile id |
| `tube_warmth` | `bool` | `false` | Analog tube saturation warmth toggle |
| `lyrics_romanize` | `bool` | `true` | Show romanized line overlay in Lyrics Studio |
| `lyrics_translate` | `bool` | `true` | Show translation overlay in Lyrics Studio |

---

## 3. UI preferences (`PlatformPrefs`)

Compose Multiplatform UI state (onboarding completion, active dark palette `NEBULA`/`OCEAN`/`EMBER`/`FOREST`/`MONO`, backdrop style, EQ preset & band gains, playback speed, skip silence, update-check snooze) is stored in:
- **Desktop:** `<dataDir>/ui_prefs.json` ([`DesktopPrefs.kt`](../android/shared/src/desktopMain/kotlin/io/github/aryan007h/musicoonline/platform/DesktopPrefs.kt))
- **Android:** `SharedPreferences("musico_ui", MODE_PRIVATE)` ([`AndroidPlatform.kt`](../android/shared/src/androidMain/kotlin/io/github/aryan007h/musicoonline/platform/AndroidPlatform.kt))

---

## 4. `sled` database (`db/`)

Opened lazily by `recommender()` in [`musico_core/src/mobile.rs`](../musico_core/src/mobile.rs). Recommender structs use frozen `bincode` layouts ([ADR-0004](adr/0004-local-first-recommender.md)); `mobile:*` trees store UTF-8 IDs or JSON.

### Recommender & library trees (`musico_recommender`)

| Tree | Key / Value | Written By |
|---|---|---|
| *(default tree)* | `adapt:weights`, `profile:vec`, `profile:ts`, `profile:n`, `profile:subprofiles`, `path:{file_path}` | `adapt.rs`, `profile.rs`, `vector_store.rs` |
| `songs` | `song_id` → bincode `SongRecord` (30-dim acoustic features) | `musico-mobile-indexer` thread (`mobile.rs`) |
| `events` | `{song_id}:{event_id}` → bincode `PlayEvent` (append-only) | `rec.on_song_ended` |
| `sessions` | `"current"` → bincode `SessionState`; `"ext"` → bincode `SessionExt` | `session.rs` (`rec.on_song_changed`, `rec.on_song_ended`) |
| `scores` | `song_id` → bincode per-song score | `recommender.rs` |
| `likes` | `song_id` / `video_id` → `LikeState` | `library.likes.set`, `library.migrate_legacy` |
| `artists` | `a:{artist}` → bincode `ArtistScoreCache` | `on_song_ended`, `bump_artist_listen` |
| `context` | time-of-day bucket → bincode context weights | `context.rs` |
| `clusters` | cluster id → bincode cluster metadata | `clusters.rs` |
| `lib:album` / `lib:artist` / `lib:track` | `id` → bincode `CatalogEntry` | `library.put`, `library.likes.set`, `library.migrate_legacy` |
| `lib:playlists` | `playlist_id` → bincode `UserPlaylist` | `playlist.*`, `library.playlist.import` |

### Core bridge trees (`musico_core/src/mobile.rs`)

| Tree | Key → Value | Purpose |
|---|---|---|
| `mobile:track_meta` | `video_id` → JSON `OnlineTrack` | Ensures Likes, Downloads, Smart Mixes, and Recommendations always have full display metadata |
| `mobile:vid_to_song` | `video_id` → `song_id` (UTF-8) | Maps online IDs to acoustically indexed `SongRecord` IDs |
| `mobile:song_to_vid` | `song_id` → `video_id` (UTF-8) | Reverse map for recommender output hydration |
| `mobile:enrich_attempted` | `video_id` → `b"1"` | Tracks legacy cached stems already queried by `library.downloads.enrich` |

---

## 5. Caches (`online/` and `covers/`)

### Stream cache (`<cache_dir>/online/`)
- **Files:** `{videoId}.{ext}` (YouTube) or `saavn_{id}.{ext}` / `saavn:{id}.{ext}` (JioSaavn), accompanied by `{stem}.meta.json` companion metadata sidecars (`write_track_sidecar`).
- **In-flight & temporary files:** `{stem}.{seq}.part`, `.ytdl`, `.tmp` (swept after `24 h` if orphaned).
- **Bounds & pruning:** Minimum valid file `10 KiB`; LRU-pruned by mtime whenever cache exceeds cap (`1 GiB` default, `online_cache_mb`, or `MUSICO_ONLINE_CACHE_MB`), shrinking to `80%` and removing matching `.meta.json` sidecars.
- **Clearing:** Settings → Storage or `online.cache.clear`.

### Cover cache (`<cache_dir>/covers/`)
- **Files:** `{sanitized_song_id}.jpg` (sanitized by `cover::sanitize_cover_stem` in [`musico_core/src/cover.rs`](../musico_core/src/cover.rs)).
- Downloaded asynchronously by `CoverJob::spawn` (`MAX_ART_BYTES = 5 MiB`). Compose UI also uses Coil3 memory/disk image caching for remote artwork URLs.

---

## 6. Resetting local state

Close Music0 completely first (so `sled` releases its file lock), then delete:
- **Linux:** `rm -rf ~/.local/share/musico-online ~/.cache/musico-online`
- **Linux Flatpak:** `rm -rf ~/.var/app/io.github.aryan007h.Music0`
- **macOS:** `rm -rf ~/Library/Application\ Support/musico-online ~/Library/Caches/musico-online`
- **Windows:** remove `%APPDATA%\musico-online` and `%LOCALAPPDATA%\musico-online`
- **Android:** Android Settings → Apps → Music0 → Storage → Clear data / Clear cache
