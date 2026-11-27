# Contributing to Musico Online

Thanks for wanting to help. Music0 (Musico Online) is a Compose Multiplatform
music player for Android and Desktop backed by a pure-Rust JNI core
(`musico_core`), a Rust audio engine (`musico_playback`), and a local recommender
(`musico_recommender`). Contributions of every
size are welcome — from a typo in the docs to a new catalog surface or playback
fix.

This guide is the contract for changes. If something here is unclear, open an
issue with the `question` label rather than guessing.

- [Code of conduct](#code-of-conduct)
- [Ways to contribute](#ways-to-contribute)
- [Prerequisites](#prerequisites)
- [Quick start](#quick-start)
- [Project layout](#project-layout)
- [How changes land](#how-changes-land)
- [Commit messages](#commit-messages)
- [Definition of done](#definition-of-done)
- [Testing](#testing)
- [Coding standards](#coding-standards)
- [Working with the docs](#working-with-the-docs)
- [Multi-agent development](#multi-agent-development)
- [Getting help](#getting-help)

---

## Code of conduct

Everyone participating is expected to follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
Be direct about code, generous about people.

## Ways to contribute

| Area | Good first issues | Notes |
|---|---|---|
| **Frontend / UI** | Shared Compose Multiplatform UI & M3E polish | `android/shared/` (CMP) |
| **Catalog surfaces** | Shelf renderers, detail-page layout, search scopes | `musico_core/src/online/catalog.rs`, `parse.rs` |
| **Playback** | Gapless edge cases, crossfade, device handling | `musico_playback/` — opinionated; discuss first |
| **Recommender** | Signals, mixes, stats, playlist logic | `musico_recommender/` — pure Rust, highly testable |
| **Online stack** | Source health, failover, metadata, lyrics | `musico_core/src/online/` |
| **Tests** | Live-test hardening, unit assertions, fixtures | `musico_core/`, `android/shared/src/desktopTest/` |
| **Docs** | Tutorials, troubleshooting, screenshots | `docs/`, `README.md`, `ROADMAP.md` |
| **Platforms** | macOS/Windows/Linux/Android builds & packaging | `.github/workflows/`, `android/app/`, `android/desktopApp/` |

Deliberate non-goals: accounts, cloud sync, social feeds, DRM content, and
lyrics licensing. See [ROADMAP.md §4](ROADMAP.md#4-deliberate-non-goals).

## Prerequisites

| Tool | Version | Why |
|---|---|---|
| **Rust** | 1.78+ (edition 2021) | Workspace `rust-version` |
| **Cargo** | Stable | Build/test Rust crates |
| **Java & Gradle** | JDK 17+, Gradle 8.10.2 (system `gradle`) | Compose Multiplatform Android & Desktop (`android/` has no wrapper) |
| **yt-dlp** | Current | Fallback stream resolver on Desktop (optional for browsing) |
| **Python 3** | 3.8+ | `scripts/gen_test_tone.sh`, static file checks |

### Linux

Install the audio build dependencies (matching `.github/workflows/ci.yml` — no WebKitGTK/GTK required):

```sh
sudo apt-get update
sudo apt-get install -y libasound2-dev pkg-config
```

On Arch:

```sh
sudo pacman -S --needed base-devel alsa-lib pkgconf
```

`yt-dlp` is available as `pacman -S yt-dlp` / `apt install yt-dlp` /
`brew install yt-dlp`.

### macOS

- Xcode Command Line Tools (`xcode-select --install`) and Java 17+.
- Release builds are unsigned; first launch may need right-click → **Open** or `xattr -cr`.

### Windows

- MSVC toolchain (`rustup default stable-msvc`) and Java 17+.
- Build from a Developer PowerShell so `link.exe` is on `PATH`.

### Android

Cross-compile `musico_core` with `cargo-ndk` (Android SDK + NDK configured),
then assemble with Gradle:

```sh
(cd musico_core && cargo ndk -t arm64-v8a -o ../android/app/src/main/jniLibs build --release --lib)
gradle -p android :app:assembleDebug
```

The pure-Rust resolver pool exists so Android can play without spawning
`yt-dlp`.

## Quick start

```sh
git clone https://github.com/ARYAN007H/Music0.git
cd Music0

# 1. Build the pure-Rust JNI core library
cargo build --release -p musico_core --lib

# 2. Run Compose Multiplatform Desktop (auto-discovers target/release)
gradle -p android :desktopApp:run
```

## Project layout

```text
android/             Compose Multiplatform app (:shared UI/ViewModel, :app Android, :desktopApp JVM)
musico_core/         Pure-Rust JNI core (`mobile.rs`), online stack, lyrics, config, identity
musico_playback/     Audio engine (decoder, CPAL, EQ, DSP)
musico_recommender/  Offline taste engine + sled persistence
flatpak/            Linux desktop entry, AppStream metainfo, and Flatpak manifest
scripts/             AppImage/Flatpak packaging scripts and test-tone generator
docs/                Deep dives (start at docs/README.md)
.github/             CI/release workflows, issue and PR templates
```

Architecture, data flow, and extension points:
[ARCHITECTURE.md](ARCHITECTURE.md).

## How changes land

Every change follows the same path:

```text
branch → commit → push → pull request → CI green → independent review → merge
```

**Never commit directly to `main`.** The repository uses PRs even for
one-line docs fixes.

### 1. Pick or open an issue

- Small fixes (typos, obvious bugs): a PR with a clear description is fine.
- Non-trivial features: open an issue first so the design can be discussed.
- Security issues: follow [SECURITY.md](SECURITY.md), **not** a public issue.

### 2. Branch

Use a descriptive prefix:

```text
feat/<short-slug>        new capability
fix/<short-slug>         bug fix
refactor/<short-slug>    behavior-preserving change
docs/<short-slug>        documentation only
test/<short-slug>        tests only
ci/<short-slug>          workflow/build changes
release/vX.Y.Z           release preparation
```

Example: `feat/queue-drag-reorder`.

### 3. Keep the diff focused

- Edit the smallest region that does the job.
- No drive-by refactors, whole-file rewrites, or reformatting of untouched
  code.
- `musico_core/src/mobile.rs` is the shared JNI command router: keep command
  dispatch consistent across `android/` callers.
- `musico_core/src/online/parse.rs` is the only response-shape-sensitive file;
  keep parsing changes there.

### 4. Open a pull request

Use the PR template. A good PR:

- states **what** changed and **why**;
- links the issue (`Closes #123`);
- lists how it was tested (commands and results);
- includes screenshots/GIFs for visible UI changes;
- notes platform impact (Linux/macOS/Windows/Android);
- calls out follow-ups rather than hiding them.

### 5. CI

`.github/workflows/ci.yml` runs two jobs on pushes to `main` and pull requests:

```sh
cargo test --workspace --exclude musico_playback
cargo build --release -p musico_core --lib
gradle -p android :app:compileDebugKotlin :desktopApp:compileKotlin :shared:desktopTest --console=plain
```

Playback e2e tests are excluded because CI runners have no audio device. The
macOS and Windows release jobs also build and package their native targets.

### 6. Independent review

A change is reviewed by someone other than its author (including agent PRs).
Reviewers check correctness, tests, failure handling, security boundaries, and
consistency with the existing architecture. Expect at least one round of
review; review comments are about the code, not the person.

### 7. Merge

Use a merge commit (the history keeps PR numbers). Delete the branch after
merge. If a release is needed, follow [docs/release.md](docs/release.md).

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/) with the
scopes this repository already uses:

```text
feat(catalog): follow browse endpoints for artist pages
fix(playback): skip queued entries whose file disappeared
refactor(online): split stream resolution into sources.rs
docs(architecture): document the stream resolution ladder
test(queue): cover reorder bounds
ci(release): gate publishing to tags
chore(release): bump version to v1.0.1
```

Rules:

- Subject in the imperative mood, ≤ 72 characters.
- Body explains **why**, not just what; wrap at ~72 columns.
- One logical change per commit; a commit should build and test on its own.
- Reference issues in the body (`Closes #53`) when applicable.

## Definition of done

A PR is ready to merge when all of these are true:

- [ ] The change does what the linked issue/PR says, and nothing unrelated.
- [ ] `cargo test --workspace --exclude musico_playback` and `cargo build --release -p musico_core --lib` pass.
- [ ] `gradle -p android :app:compileDebugKotlin :desktopApp:compileKotlin :shared:desktopTest --console=plain` passes when `android/` or JNI contracts change.
- [ ] New behavior has a hermetic test (Rust unit/integration or Kotlin `SharedCoreTests`).
- [ ] Compose Multiplatform changes were exercised on Desktop and/or Android; visible work has screenshots in the PR.
- [ ] Version sync checked across all 5 manifests if releasing ([docs/release.md](docs/release.md#1-versioning)).
- [ ] Network/disk work is off the UI thread and bounded.
- [ ] Error paths return a user-visible message; no new `unwrap()` on runtime
      data.
- [ ] Security-sensitive input (URLs, ids, filenames, downloaded bytes) is
      validated at the boundary.
- [ ] Docs are updated when behavior, setup, IPC, or architecture changes.
- [ ] `CHANGELOG.md` has an entry for user-visible changes.
- [ ] Independent review approved.
- [ ] CI is green.

## Testing

The full test strategy is in [docs/testing.md](docs/testing.md). The commands
you will use most:

```sh
# Rust suite CI runs (live tests are #[ignore]-gated by default)
cargo test --workspace --exclude musico_playback

# Full workspace, including the audio-device end-to-end tests
bash scripts/gen_test_tone.sh
cargo test --workspace

# Compose Multiplatform Kotlin compile + shared JVM tests
gradle -p android :app:compileDebugKotlin :desktopApp:compileKotlin :shared:desktopTest --console=plain

# Opt-in live network tests (needs network; some need yt-dlp)
cargo test -p musico_core live_ -- --ignored --nocapture
```

### When to add which test

| Change | Test |
|---|---|
| Pure Rust logic, parser, cache policy | `#[cfg(test)]` unit test beside the code, no network |
| Cache/prune/download behavior | Hermetic test using `MUSICO_CACHE_DIR` tempdir |
| JNI `dispatch()` command (`mobile.rs`) | Rust test in `musico_core/src/mobile.rs` + Kotlin test in `SharedCoreTests.kt` |
| Compose Multiplatform UI / ViewModel | `gradle -p android :app:compileDebugKotlin :desktopApp:compileKotlin :shared:desktopTest` |
| Real catalog/shape verification | `#[ignore]` `live_*` test, run manually |

## Coding standards

### Rust

- **Format:** `cargo fmt` (default rustfmt settings).
- **Lint:** `cargo clippy --workspace --all-targets -- -D warnings` is
  recommended locally; CI currently runs tests only.
- **Errors:** `thiserror` in library crates; `Result<T, String>` at the IPC
  boundary. Never `unwrap()` on user input, network data, filesystem results,
  or lock poisoning. The established poison recovery is
  `unwrap_or_else(|e| e.into_inner())` where continuing is safe.
- **Blocking work:** network, yt-dlp, scanning, and large file I/O never run on
  the UI thread. Use `spawn_blocking` for async commands or a named OS thread.
- **Bounded loops:** retries, queue skips, speculative downloads, and caches
  need explicit caps.
- **Logging:** `log::{debug, info, warn, error}`. Use `warn` for something the
  user/operator should act on; `debug` for expected failover noise.
- **Parsing:** all InnerTube response parsing belongs in `online/parse.rs`.
  Keep pure decisions (staleness, shelf merging, stream selection) in testable
  functions.
- **Dependencies:** prefer std and existing crates. A new dependency needs a
  reason in the PR (size, license, maintenance, platform support).
- **Platform code:** gate with `#[cfg(target_os = "…")]` and keep the Linux
  build compiling. macOS CI type-checks the macOS-only blocks; Windows CI does
  the same for Windows.

### Compose Multiplatform (Kotlin)

- **Material 3 Expressive only:** Build new screens, sheets, and controls from `Theme.kt` (`ThemeMode`, `MusicoSpacing`, `MusicoShapes`, `MusicoTypography`, 5-rung tonal surfaces), `Palettes.kt`, `ExpressiveShapes.kt`, `components/ExpressiveControls.kt`, and `components/Common.kt` (see [docs/brand/BRAND_GUIDELINES.md](docs/brand/BRAND_GUIDELINES.md) and [docs/frontend.md](docs/frontend.md)).
- **Zero-blur / zero-gradient backdrops:** `Backdrops.kt` uses solid stepped M3E tonal surfaces and geometric vector shapes (no blurs or smeared gradients) across Light and Dark modes.
- **Safe dynamic geometry:** Clamp animated corners (`safeCornerDp` / `coerceAtLeast(0.dp)`) and scales (`safePositiveScale`) so springs never overshoot negative.
- **Keep JNI off the UI thread:** All `MusicoCore.call()` / `callElement()` calls run on `Dispatchers.IO`; preserve state across the `840.dp` breakpoint.

## Working with the docs

Documentation is part of the product:

- **Behavior change** → update the relevant `docs/` page and `CHANGELOG.md`.
- **Architecture change** → update `ARCHITECTURE.md` and, if it is a durable
  decision, add an ADR in `docs/adr/`.
- **New JNI command or bridge change** → update `docs/ipc.md`.
- **New env var** → update `ARCHITECTURE.md` and `docs/data-storage.md`.
- **New setup step** → update `CONTRIBUTING.md` and `docs/development.md`.

Write for a contributor who has the repository but not your context. Prefer
commands, file paths, and failure modes over vague prose.

## Multi-agent development

Part of this repository uses parallel AI-assisted development alongside human
contributions:

1. Read [`agentchat.md`](agentchat.md) for active product decisions and recent task context.
2. Keep changes scoped to a dedicated branch and follow the standard PR + CI + independent review workflow.

## Getting help

- Start with [docs/troubleshooting.md](docs/troubleshooting.md).
- Search existing issues before opening a new one.
- For design questions, open an issue with the `question` label and enough
  context to reproduce or evaluate the idea.
- Never post credentials, personal paths, or download links in an issue.

Thanks for making Musico Online better.
