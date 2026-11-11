# Changelog

All notable changes to Musico Online are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) with an `-online`
prerelease suffix.

The desktop app version lives in three places and must stay in sync:
`src-tauri/Cargo.toml`, `src-tauri/tauri.conf.json`, and `package.json`.
Release tags are `v<version>-online` (for example `v0.7.0-online`). Any tag
containing a hyphen is published as a GitHub prerelease.

## [Unreleased]

### Added (Desktop)

- **Material 3 Expressive design language** — a second, whole-app skin alongside
  the default Cupertino look. Settings → Themes → Design Language flips every
  surface live (no restart): tonal surfaces derived from the active accent
  palette via `color-mix()` (dynamic color), the M3 shape scale (4/8/12/16/28/
  full), pill navigation indicators, M3 switches and filter chips, expressive
  motion (emphasized easings, springy hover lifts, shape-morphing presses) —
  all transform/opacity only, with a `prefers-reduced-motion` fallback. The
  skin rides in as `html[data-skin="m3e"]` in a new final CSS layer
  (`m3-expressive.css`); the iOS look is untouched. Persisted in settings
  (`skin`) and localStorage; the WCAG contrast gate now covers both skins
  across every palette × theme (144 pairs, all ≥ 4.5:1).

### Improved (Desktop)

- **Online tracks now loudness-normalize.** The InnerTube-direct player
  response's `audioConfig` loudness (the same `loudnessDb`/`perceptualLoudnessDb`
  the Metrolist/InnerTune clients use, referenced to −7 LUFS) is captured per
  track and applied as a static gain toward the user's LUFS target, clamped to
  −15..+3 dB. Local files keep scan-computed ReplayGain; tracks resolved via
  Piped/yt-dlp/Saavn reset to unity instead of inheriting the previous track's
  gain (a quiet leak the old code had).
- **Fast-path audio quality follows the quality tiers.** `high`/`lossless`
  unlock the itag-141 class (256 kbps AAC) in the Piped/InnerTube-direct fast
  path — previously hard-capped at 192 kbps while yt-dlp already fetched 256k
  on those tiers.

## [0.9.4] - 2026-11-12

### Fixed (Android native)

- International recommendations (the J-POP shelf: "死ぬのがいいわ -
  Shinunoga E-Wa", "すずめ - Suzume (feat. Toaka)") failed to play while
  Indian/English songs worked. Three compounding causes in the JioSaavn
  cross-source fast path, the layer everything rides on: a ±4 s duration
  veto rejected the right song outright (releases drift across platforms
  — YTM 215 s vs saavn's 185 s single edit), the 3 s API timeout missed
  on carrier networks, and mixed-script titles ("すずめ - Suzume") got
  zero search results. Duration now RANKS candidates instead of vetoing
  them (>20% relative drift still disqualifies a different recording),
  the artist match ignores punctuation ("RADWIMPS, Toaka"), query
  variants cover YTM's "native - romanized" convention, and the API
  timeout doubles to 6 s. Proven live on both reported tracks.
- The InnerTube-direct director now chains per-profile failure reasons
  in its error (only the last was visible before), for diagnosability.

## [0.9.3] - 2026-11-12

The update experience becomes first-class, and the shell looks the way a
floating dock always should. Proven end-to-end on-device (Android 17):
release check → notification + banner → download → sha256 verification →
system installer hand-off.

### Added (Android native)

- Release notifications: the updater checks GitHub releases on **every
  app launch** (the old once-per-24 h gate buried the only update
  channel), and a newly discovered release posts a system notification —
  once per version, tapping it opens the app and starts the download
  immediately. POST_NOTIFICATIONS is requested once on first launch
  (Android 13+).
- The in-app update banner is a floating popup now: docked below the
  status bar, never pushing the page down or sliding under the clock,
  with live MB download progress in place of the label.
- A fully downloaded + digest-verified APK is reused when retrying
  (e.g. after granting "Install unknown apps") — no 40 MB re-download
  for the same release.
- Dispatch builds can pose as an older version (compile-time
  MUSICO_VERSION_OVERRIDE via the release workflow inputs) to exercise
  the whole update flow against the latest published release without
  cutting a fake tag or tripping downgrade protection.

### Fixed (Android native)

- The mini player + pill dock no longer reserve an opaque black band at
  the bottom of the screen: the feed scrolls behind the floating pills
  (the screens' bottom content padding keeps the last rows reachable),
  which is how a floating dock was always meant to look.

## [0.9.2] - 2026-11-12

First release delivered **through the in-app updater itself**: installs on
0.9.1 see the banner, download with progress, verify the published sha256
digest, and hand off to the system installer — the end-to-end exercise of
the 0.9.1 updater on a real device (Android 17).

## [0.9.1] - 2026-11-11

Bug-fix round from the first external testers' reports.

### Fixed

- Lyrics could come from a completely different song: shelf-built tracks
  carry no duration, and the LRCLIB duration-less fallback handed the
  SHORTEST same-titled song worldwide to the lyric stage ("lyrics are a
  mix of english and chinese" on Backbone). Shelf items now carry
  `duration_secs` (list rows on detail pages + two-row subtitle tails),
  and duration-less LRCLIB matches pass a title/artist word-set
  similarity gate — foreign songs are rejected and the YTM plain-text
  fallback (always the right song's lyrics) takes over. Proven by
  `live_lyrics_durationless_no_foreign_song`.
- "First one is not playing": a failed resolve skipped to the next track
  SILENTLY — the skip now names the track ("Couldn't play X — skipped to
  the next song") so dead tracks aren't mistaken for wrong taps. The skip
  path was also reworked after the pre-merge review: it no longer races
  live queue state (stale failures can't remove the track the user just
  asked for), never auto-skips in Airplane Mode (where every track fails
  and the old chain deleted the entire queue one track per instant), and
  caps consecutive auto-skips at 3 with one honest message.
- 15–20 s first-play latency, mitigated: the next two queue tracks are
  resolved in the background the moment a song starts (on the core's
  generous background budget, never holding the interactive slot — a
  `{"background":true}` flag on `track.resolve`), so advances hit the
  stream cache instead of the 10 s fast-path pool; `resolve` also logs
  its fast-path timing (info) for on-device diagnosis via logcat.
  Offline page saves ("Save offline") run on the same background budget.
- Release years no longer poison lyrics matching: a two-row subtitle
  ending in a year ("… • 2023") parsed as a 33-minute duration, which
  silently disabled the LRCLIB exact-duration path for that track.
  Duration extraction now requires an explicit `m:ss` colon shape.

### Hardened (pre-merge security review)

- Updater integrity: the downloaded APK is verified against the GitHub
  release's published sha256 digest before the installer is offered
  anything (exact-size fallback on digest-less assets), a size ceiling
  aborts oversized streams mid-flight, and the download is genuinely
  cancellable (cooperative suspension between chunks).
- A release year / bare number can no longer smuggle a bogus duration
  into the lyrics path (see above); LRCLIB's exact endpoint is also
  tried with artist+title alone for duration-less tracks, behind the
  same word-set gate.
- JNI/init robustness: `initNative` is idempotent and panic-safe (an
  Activity recreation can no longer trip the logger's init_once and
  abort the app), an OOM while building a bridge reply throws a real
  Java exception instead of returning a null ref, `videoId` args are
  charset-gated, and `settings.save` serializes its read-merge-write
  cycle so overlapping toggles can't lose a write.
- Toast timer restarts for identical consecutive messages (keyed on a
  monotonic error id, not the text); loadMore failures log breadcrumbs
  instead of vanishing; personalized shelves now carry durations; the
  mini-player equalizer stops ticking the frame clock while paused; the
  Android release workflow derives versionCode/versionName from the
  release tag instead of a five-way manual lockstep.
## [0.9.0] - 2026-11-11

The native Android app release: the phone experience moves from the Tauri
webview to a full native Kotlin (Compose + Media3) app over the same Rust
core. Desktop is unchanged in behavior and ships the 0.8.2-era core
improvements (InnerTube-direct multi-client director, 48 kHz oboe output).

### Added (Android native)

- Full app shell: Home / Radio / Library / Search under a Material 3
  expressive pill island, big-brand top bar with profile avatar menu,
  mini player with live progress, animated tab transitions.
- Home: Apple-Music-style layout — auto-advancing hero carousel, signature
  "Made for you" / "Trending" grids with rank numerals, cover carousels,
  skeleton loading. Truly endless browsing: continuation paging when the
  feed offers it, then editorial charts and an endlessly-deep genre chain
  (mood/genre style pages are browsed two at a time on every scroll-to-end).
- Onboarding: pick preferred languages and at least three favorite
  artists; the home feed gets "Because you like …" radio shelves and
  language pick shelves prepended, persisted locally, rebuilt only when
  missing.
- Radio tab: the whole mood/genre station grid as tinted tiles; every
  tile opens a detail page of station playlists.
- Detail pages (album / playlist / artist / genre): hero header, Play /
  Queue all / Save offline actions, numbered track list, remaining
  shelves. Nested navigation (album → artist → …).
- Full-screen player: artwork / lyrics / Up Next swipe pages, squishy M3
  play FAB, seek, buffering + resolving states, frame-clock position
  interpolation for word-accurate lyrics.
- Word-by-word lyrics: one huge fill-animated word with small previous /
  next neighbors, tap-to-seek, line-granularity fallback — the same
  LRCLIB word-timing synthesis the desktop uses (single implementation
  shared with the JNI bridge).
- Backdrops: Midnight (black), hand-rolled Canvas Rain, Aurora veils,
  blurred Artwork Glow (default; RenderEffect on API 31+).
- Equalizer: real audiofx Equalizer + BassBoost bound to the ExoPlayer
  session, band sliders, gain-curve presets, bass boost; Settings gains
  Skip silence and playback speed.
- Themes: five color-theory palettes (Nebula, Ocean, Ember, Forest, Mono)
  with runtime switching and swatch previews.
- Long-press track sheet: play next, add to queue, save offline, like.
- Settings: streaming-quality chips, cache usage + cap slider + clear
  (new `online.cache.usage/clear` bridge commands), airplane mode,
  autoplay — core values round-trip through `settings.save`.

### Fixed

- Android silent audio root cause: cpal/oboe enumerated a real device as
  a degenerate "5512 Hz, 1 ch" range — the engine now refuses capped
  ranges and requests 48 kHz on Android (desktop logic unchanged).
- Launch crash of the first native APK: JNI symbol naming must carry the
  full Kotlin package path.
- Mobile JNI bridge contract: `data` is object-shaped; array-shaped
  replies (`online.related`) collapsed to `{}` silently — the Rust side
  wraps arrays and the Kotlin bridge is strict (loud error, plus
  `callElement` for raw shapes).
- Lazy list key collisions: YTM mood cards share one browseId
  (`FEmusic_moods_and_genres_category`), which crashed Radio and deep
  Home scrolls; every Lazy key carries its index.
- Genre shelf decoding: unknown `ItemKind` values degrade gracefully
  instead of failing whole pages.

### Changed

- Android release APKs are built by the native gradle job (version 0.9.0,
  code 90000); the Tauri android job retires at native parity (#111).
## [0.8.1-online] - 2026-11-06

Packaging release: the repository is now `ARYAN007H/Music0` and the bundle
identity follows it.

### Changed

- GitHub repository renamed from `Musico-online` to `Music0`; all project,
  release, clone and screenshot URLs use the new name.
- Tauri identifier, Flatpak app id, desktop entry and AppStream metadata are
  now `io.github.aryan007h.Music0`. Storage paths are unchanged
  (`~/.local/share/musico-online`, `~/.cache/musico-online`,
  `~/.config/musico-online`), so there is no data migration.
- Flatpak runtime updated to `org.gnome.Platform//51`.
- Flatpak builds `yt-dlp` from its shipped Python sources instead of
  installing the prebuilt zipapp from the release tarball.
- Removed the unnecessary `xdg-download` filesystem permission; downloads stay
  in the app-private cache.
- Removed the redundant `--own-name` for the app id (Flatpak grants the app's
  own name automatically).

### Fixed

- Bundle homepage and packaging metadata point at the new repository URL.

## [0.8.0-online] - 2026-11-06

Packaging release: the app is now distributed as a Flatpak, and the bundle
identity moved to a GitHub namespace.

### Added

- Flatpak manifest, AppStream metadata and desktop entry under `flatpak/`, with
  `yt-dlp` bundled inside the sandbox so downloads work out of the box.
- `docs/flatpak.md` covering local builds, sandbox differences and permissions.

### Changed

- Tauri identifier is now `io.github.aryan007h.MusicoOnline`. Storage paths are
  unchanged (`~/.local/share/musico-online`, `~/.cache/musico-online`,
  `~/.config/musico-online`), so there is no data migration.
- `frontend/icons/icon-512.png` is a real 512×512 render instead of a 256×256
  file named as if it were 512.

### Fixed

- Windows: namespaced stream-cache files replace `:` with `_` in the filename
  (the colon is reserved on Windows). Legacy raw-named cache files still
  resolve.
- Windows release builds run the Tauri CLI under bash so the `msi,nsis` bundle
  list is passed intact instead of being split by PowerShell.

## [0.7.0-online] - 2026-11-05

Cross-platform release: native macOS and Windows window chrome, macOS/Windows
builds on the release train, synced-lyrics customization, and quality tiers
that are actually applied.

### Added

- **macOS support** — universal `.dmg`/`.app` builds (Apple Silicon + Intel),
  native App/Edit/View/Window menus with standard shortcuts, overlay traffic
  lights in the sidebar strip, and SF Pro system typography.
- **Windows support** — `.msi`/NSIS installers and a standalone executable,
  with the native title bar removed at runtime and drawn min/max/close
  controls wired to the Tauri window API.
- **Lyrics customization** — romanized pronunciation and English translation
  overlays for synced lyrics, plus expanded-player lyric controls.
- **Streaming and download quality tiers** — `low` (96 kbps), `normal`
  (160 kbps), `high` (320 kbps), `lossless` where the source provides it.
  JioSaavn direct URLs and yt-dlp format selection both honor the setting.
- **Platform capabilities command** — exposes the platform name and the
  resolvers available on it (for example: no `yt-dlp` on Android).
- **Mobile/PWA polish** — responsive layout and install/lock-screen metadata
  improvements.

### Changed

- Playback configuration is applied consistently at startup and after settings
  changes.
- Download quality is no longer an echo-only setting: background resolves use
  `download_quality`, interactive playback uses `streaming_quality`.
- Removed the output sample-rate pills. The output stream follows the device's
  native rate; the old control did not change engine behavior.

### Fixed

- Windows cache filenames sanitize namespaced song ids (`yt:…`, `saavn:…`), so
  a colon can no longer produce an invalid path.
- Windows-specific cache-name contract tests.
- Windows release builds run `tauri build` under bash, avoiding PowerShell
  argument splitting for `--bundles msi,nsis`.
- `extract_visuals` now uses the first visual instead of relying on a loop
  that never executed.

## [0.6.1-online] - 2026-11-02

Lyrics and UX follow-up release.

### Added

- **Synced (karaoke) lyrics via LRCLIB** — timed line highlighting for online
  songs, with an exact metadata lookup followed by a `±2 s` duration-filtered
  search. `MUSICO_LRCLIB_API` points the client at a mirror or proxy.
- **Guided empty states** for Home, Browse, search, library, and playlists.
- **Real playlist-name modal** — `window.prompt` retired.
- **Header status collapse** — the empty browse status line no longer takes
  space when there is nothing to report.
- **Favorite online songs** — liked `yt:`/`saavn:` tracks now appear in
  Favorites and survive a restart.

### Fixed

- Expanded-player likes persist for online references.
- IPC argument casing in the synced-lyrics path (`camelCase` from JS).
- Local-like data is not polluted by online placeholders.
- Accessibility and review follow-ups from the round-4 UX pass.
- LRCLIB environment override is honored by tests and live runs.

## [0.6.0-online] - 2026-11-02

Multi-source playback and catalog-depth release.

### Added

- **JioSaavn catalog source** — independent search merged into the first YT
  results page, with direct 320 kbps media URLs and instant playback.
- **Piped/Invidious direct-URL fast path** — resolves the same YouTube catalog
  without spawning `yt-dlp`, under a bounded ~10 s budget, with per-instance
  health ordering and environment overrides.
- **Cross-source resolution** — exact title/artist/duration matches on
  JioSaavn can satisfy a YouTube track before extraction is attempted.
- **User playlists** — local, sled-backed, ordered playlists with create,
  rename, delete, add/remove/move, and restart-safe tracks.
- **Library pages** — albums, artists, tracks, Recently Added, and Favorites.
- **Batch downloads** — album/playlist Download All with at most two
  concurrent resolves, persistent pending work, retry-failed, and cancel.
- **Airplane mode** — online commands fast-fail; cached views keep working;
  downloads queue and resume when the mode is lifted.
- **Search typeahead** — debounced InnerTube suggestions with keyboard
  navigation and silent degradation.
- **First-run onboarding** and a never-empty Home feed (YT Music charts
  backfill sparse personalized shelves).
- **Apple Music-style shell** — persistent transport pill, expanded player,
  queue panel with reorder/jump, context menus, Liquid Glass materials.
- **PWA/mobile runtime** — manifest, service worker, install flow, and
  MediaSession lock-screen controls.

### Changed

- The playback pill and expanded player are a single player surface: the pill
  hides while the full player is open.
- Download badges and storage usage reflect partial downloads and sidecars.

### Fixed

- Review findings across search routing, row badges, playlists, and queue
  mutations.
- Playback of queued entries whose files disappeared.
- Asset cache-busting for the WebKit webview.

## [0.5.0-online] - 2026-10-29

First tagged Musico Online release. This is the initial public baseline for the
online companion app and its release pipeline.

### Added

- **InnerTube search** against YouTube Music with the `WEB_REMIX` client,
  Songs/Videos filters, tolerant parsing, and continuation paging.
- **Online playback** through a resolved local file and the shared offline
  audio engine — no second streaming path.
- **Tiered stream resolution** — cache hit, then direct-URL sources, then
  `yt-dlp`, with per-video locks, one retry, size verification, and a hard
  timeout.
- **YouTube Music radio** via the `next` endpoint, a 12-entry recent-play ring
  buffer, and background auto-continue.
- **Download hardening** — duplicate-download locks, transient retry, minimum
  size checks, duration probing, and cover-art caching.
- **Cache hygiene** — 1 GB default cap, mtime-based LRU pruning to 80%, and
  `MUSICO_ONLINE_CACHE_MB` override.
- **Browse home screen** with skeleton shimmer, quick picks, and
  click-to-play rows; fullscreen-only vinyl.
- **Release pipeline** — tag-driven Linux and macOS builds with checksums and
  GitHub Releases.

[Unreleased]: https://github.com/ARYAN007H/Music0/compare/v0.8.1-online...HEAD
[0.8.1-online]: https://github.com/ARYAN007H/Music0/compare/v0.8.0-online...v0.8.1-online
[0.8.0-online]: https://github.com/ARYAN007H/Music0/compare/v0.7.0-online...v0.8.0-online
[0.7.0-online]: https://github.com/ARYAN007H/Music0/compare/v0.6.1-online...v0.7.0-online
[0.6.1-online]: https://github.com/ARYAN007H/Music0/compare/v0.6.0-online...v0.6.1-online
[0.6.0-online]: https://github.com/ARYAN007H/Music0/compare/v0.5.0-online...v0.6.0-online
[0.5.0-online]: https://github.com/ARYAN007H/Music0/releases/tag/v0.5.0-online
