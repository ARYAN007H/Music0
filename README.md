# Musico Online

**A local-first, Apple Music-class player with free online music.** Musico
Online is the companion repo to [Musico](https://github.com/ARYAN007H/Musico):
a clean-room Tauri 2 desktop app that combines a real audio engine, a local
taste engine, and an online catalog layer for YouTube Music and JioSaavn.

The streaming layer uses the same public technique as Metrolist / InnerTune /
RiMusic (InnerTube + direct-URL resolvers + yt-dlp). It lives here, **not** in
the offline Musico release, and is intended for personal use.

**Status:** v0.7.0 (`v0.7.0-online`, 2026-11-05) · Linux · macOS · Windows ·
Android (local build) · installable mobile PWA.

<p align="center">
  <img src="docs/screenshots/library-light.png" width="42%" alt="Musico Online — light theme"/>
  <img src="docs/screenshots/library-dark.png" width="42%" alt="Musico Online — dark theme"/>
  <img src="docs/screenshots/library-compact.png" width="13.5%" alt="Musico Online — compact width"/>
</p>

## What it does

- **Search and play millions of tracks** through YouTube Music's InnerTube
  catalog, with Songs/Videos filters, continuations, and live typeahead.
- **JioSaavn as a second catalog and stream source** — direct AAC URLs up to
  320 kbps (quality-tier dependent), merged into the first search page.
- **Tiered stream resolution** — cache → exact JioSaavn cross-source match →
  Piped/Invidious/InnerTube Direct fast path → `yt-dlp` fallback. One audio
  path: every result becomes a local file played by the same engine as your
  library.
- **Browse everything** — Home shelves, mood/genre grid, album, artist, and
  playlist detail pages, related tracks, and YouTube Music radio.
- **A real library** — local folder scanning, saved albums/artists/tracks,
  playlists, favorites, recently added, and A–Z views.
- **Downloads that behave** — single-track and batch downloads, two concurrent
  resolves, persistent pending work, retry/resume, a 1 GB LRU cache (cap
  configurable), and an Airplane Mode that fast-fails online features and
  queues work.
- **Lyrics** — unsynced YouTube Music text plus LRCLIB synced lyrics with
  karaoke highlighting, optional romanization and translation.
- **Playback depth** — gapless playback, crossfade, EQ, loudness
  normalization, AutoEQ profiles, and a local recommender v2 that learns from
  listens, skips, likes, artists, context, and audio features.
- **Native everywhere it can be** — MPRIS on Linux, SMTC on Windows,
  MPNowPlayingInfoCenter on macOS, and a no-build UI that runs in the system
  webview.

## Install

Download the latest bundles from
[GitHub Releases](https://github.com/ARYAN007H/Musico-online/releases/latest).

| Platform | Artifacts |
|---|---|
| **Linux x86_64** | `.deb`, `.rpm`, `.AppImage`, or the bare binary in a `.tar.gz` |
| **macOS universal** | `.dmg` and `.app.tar.gz` (Apple Silicon + Intel) |
| **Windows x86_64** | `.msi`, NSIS `.exe`, or a standalone `.exe` |
| **Android** | Build locally: `cargo tauri android init && cargo tauri android build --apk` |
| **iOS / mobile web** | Open the web app in Safari/Chrome and use **Install App** / **Add to Home Screen** |

The macOS and Windows builds are **not code-signed**. On macOS, right-click the
app → **Open** on first launch, or run:

```sh
xattr -cr "/Applications/Musico Online.app"
```

Windows SmartScreen may show a warning for the unsigned installer.

### Requirements

- A desktop session with a working audio output device.
- Network access to `music.youtube.com` and the configured stream sources.
- **`yt-dlp` on `PATH`** is strongly recommended. Playback works without it
  while a direct-URL source is healthy, but `yt-dlp` is the honest last resort.
  - Arch: `sudo pacman -S yt-dlp`
  - Debian/Ubuntu: `sudo apt install yt-dlp`
  - macOS: `brew install yt-dlp`
  - Windows: `winget install yt-dlp.yt-dlp`

Keep `yt-dlp` updated — YouTube extraction changes frequently.

## Build from source

Prerequisites: Rust stable (MSRV 1.78), Node.js 20+ for the web preview and
tests, the Tauri 2 CLI, and your platform's Tauri system dependencies. The full
list is in [docs/development.md](docs/development.md).

```sh
git clone https://github.com/ARYAN007H/Musico-online.git
cd Musico-online

# Rust app
cargo build -p musico-online

# Web preview / TypeScript tests
npm install

# Tauri CLI
cargo install tauri-cli --version '^2'
# or: npm install --global @tauri-apps/cli@^2
```

### Desktop development

The frontend has no bundler. Tauri's `devUrl` points at port 1420, so serve the
static files in one terminal and run the native shell in another:

```sh
# terminal 1
python3 -m http.server 1420 --directory frontend

# terminal 2
cargo tauri dev
```

### Production bundle

```sh
cargo tauri build
# Linux:   target/release/bundle/{deb,rpm,appimage}/
# macOS:   target/release/bundle/dmg/
# Windows: target/release/bundle/{msi,nsis}/
```

For an Apple Silicon + Intel universal macOS build:

```sh
rustup target add aarch64-apple-darwin x86_64-apple-darwin
cargo tauri build --target universal-apple-darwin
```

### Web / PWA preview

The Express + TypeScript runtime is a demo and UI development environment, not
the production backend:

```sh
npm run dev              # http://localhost:3000
PORT=8143 npm run dev    # port used by the browser suites
```

It serves the same `frontend/` assets with a seeded catalog and synthesized
audio, and binds `0.0.0.0`. Do not expose it to an untrusted network.

## How playback works

Streaming is deliberately boring: resolve a track to a file, then hand that
file to the offline audio engine.

```text
search / browse / radio
        │
        ▼
   OnlineTrack { source, video_id, metadata }
        │
        ▼
 resolve_track()
   1. stream-cache hit
   2. exact JioSaavn cross-source match (YouTube tracks)
   3. Piped / Invidious / InnerTube Direct fast path (~10 s budget)
   4. yt-dlp (interactive: 1 × 90 s; background: 2 × 120 s)
        │
        ▼
   verified local file  →  musico_playback  →  speakers
```

- The cache lives at `~/.cache/musico-online/online/` and is pruned LRU to a
  configurable cap (1 GB default, `MUSICO_ONLINE_CACHE_MB` or the Storage
  settings slider override).
- A global cap of three `yt-dlp` children prevents interactive, pre-resolve,
  batch, and radio-warmup work from stacking.
- Downloads write a `.part` file, verify a minimum size, then rename
  atomically, so a killed process cannot publish a truncated track. Duration is
  probed when the search metadata lacks it.
- Online radio warms the next pick while the current track plays; autoplay and
  warmup are both gated by the Autoplay setting.

The full ladder, source trust model, and failure behavior are documented in
[docs/online-stack.md](docs/online-stack.md).

## Testing

```sh
# What CI runs: 172 collected tests, 13 live tests ignored by default
cargo test --workspace --exclude musico_playback

# Full workspace: 189 tests, including playback end-to-end (needs an audio device)
bash scripts/gen_test_tone.sh
cargo test --workspace

# TypeScript engine: 20 tests + typecheck
npm test
npm run build

# Opt-in live network tests
cargo test -p musico-online live_ -- --ignored --nocapture
```

Browser suites and the WCAG contrast gate are documented in
[docs/testing.md](docs/testing.md). They expect the web preview on port 8143
(contrast gate: 8145) and Playwright + Chrome installed locally.

## Documentation

| Document | Contents |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | System design, threads, crate boundaries, flows, failure model, budgets |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Setup, branch/PR workflow, commits, testing, standards, multi-agent rules |
| [docs/](docs/README.md) | Deep dives: development, testing, frontend, online stack, storage, IPC, release, troubleshooting |
| [docs/adr/](docs/adr/README.md) | Architecture decision records and their rationale |
| [SECURITY.md](SECURITY.md) | Supported versions, threat model, reporting, privacy |
| [CHANGELOG.md](CHANGELOG.md) | Release history from v0.5.0 to v0.7.0 |
| [ROADMAP.md](ROADMAP.md) | Plan of record and remaining phases |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | Contributor Covenant 2.1 |

## Project layout

```text
src-tauri/            Tauri app: IPC commands, state, online stack, platform glue
musico_playback/      Audio engine: decoder, CPAL output, queue, DSP
musico_recommender/   Local recommender + sled persistence
frontend/             No-build UI: classic scripts + vendored Preact/htm shell
src/engine/           TypeScript engine for the web/PWA preview
server.ts             Express demo runtime (not the production backend)
scripts/              Browser suites, contrast gate, test-tone generator
docs/                 Deep dives and ADRs
.github/              CI/release workflows, issue and PR templates
```

## Contributing

Every change lands as **branch → PR → CI → independent review → merge** —
never directly on `main`. Good first targets are frontend polish, catalog
surfaces, tests, and documentation; the Rust playback core is intentionally
conservative.

Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a PR. Human PRs go
through the same review gate as agent PRs. The repository runs a multi-agent
experiment coordinated through the tracked [agentchat.md](agentchat.md) hub;
you do not need to participate in it to contribute.

- Bug reports and feature requests: use the issue templates.
- Security issues: **do not open a public issue** — follow [SECURITY.md](SECURITY.md).
- Community expectations: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## Known limitations

- **Online sources are unofficial.** InnerTube response shapes, instance
  availability, and YouTube extraction can change without notice. `yt-dlp`
  needs regular updates; the direct sources and JioSaavn absorb some outages.
- **Cross-source matching is heuristic.** A YouTube track is mapped to
  JioSaavn by title, artist token, and a ±4 s duration window. If the wrong
  take is picked, there is no per-track override in v0.7.0; report the
  YouTube id and the Saavn id so the matching heuristic can be fixed.
- **Quality is source-bounded.** `lossless` means "best available from the
  source", not a guarantee of a lossless master.
- **Prebuilt macOS/Windows artifacts are unsigned.** Gatekeeper and SmartScreen
  will warn on first launch.
- **No cloud account or sync.** Library metadata, settings, playlists, likes,
  and history stay on the machine.
- **Android is a local build.** The APK is not published by CI; the pure-Rust
  resolvers work without `yt-dlp`, and the desktop folder picker is not
  available on Android.
- **The web preview is not the app.** It has a demo backend, a seeded catalog,
  and synthesized audio. Use the Tauri build for real playback.

## License

MIT — see [LICENSE](LICENSE).
