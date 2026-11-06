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

[Unreleased]: https://github.com/ARYAN007H/Musico-online/compare/v0.7.0-online...HEAD
[0.7.0-online]: https://github.com/ARYAN007H/Musico-online/compare/v0.6.1-online...v0.7.0-online
[0.6.1-online]: https://github.com/ARYAN007H/Musico-online/compare/v0.6.0-online...v0.6.1-online
[0.6.0-online]: https://github.com/ARYAN007H/Musico-online/compare/v0.5.0-online...v0.6.0-online
[0.5.0-online]: https://github.com/ARYAN007H/Musico-online/releases/tag/v0.5.0-online
