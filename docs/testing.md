# Testing

Music0 uses five test layers: hermetic Rust tests, Compose Multiplatform Kotlin checks, opt-in live network tests, TypeScript engine tests, and web-preview browser assertion suites. The CI workflow runs Rust, Compose Multiplatform, and TypeScript checks on Linux; the release workflow runs native builds across Linux, macOS, Windows, and Android.

## 1. Test layers

| Layer | Runs in CI | Needs |
|---|---|---|
| Rust workspace (`--exclude musico_playback`) | Yes (`rust-core`) | Rust toolchain + `libasound2-dev` |
| — `musico_core` | Yes (`#[ignore]` live tests skipped; `mobile.rs` + `stream_server.rs` included) | Rust |
| — `musico_recommender` | Yes | Rust |
| — `musico_playback` | **No** (excluded in CI; 27 unit tests + 4 device E2E tests) | Audio device + test WAV for E2E |
| Compose Multiplatform (`:app:compileDebugKotlin`, `:desktopApp:compileKotlin`, `:shared:desktopTest`) | Yes (`compose-multiplatform`) | Java 17 + Gradle 8.10.2 + Android SDK |
| TypeScript engine (`npm test`, `npm run build`) | Yes (`web-engine`) | Node 20 + `npm install` |
| Browser suites (`scripts/browser_*_test.mjs`) | No | Web preview + Playwright + Chrome |
| Contrast gate (`scripts/contrast_gate.mjs`) | No | Web preview + Playwright + Chrome |

CI's Rust test command is:

```sh
cargo test --workspace --exclude musico_playback
```

Why `musico_playback` is excluded in CI: `musico_playback/tests/engine_e2e.rs` opens a real audio device and plays a real file, and GitHub runners have no audio device. Run `cargo test -p musico_playback --lib` locally for the 27 hermetic unit tests (including `http_source.rs`, `queue.rs`, `eq.rs`, `decoder.rs`), or run the full crate with a test tone.

## 2. Rust tests

### Standard run

```sh
cargo test --workspace --exclude musico_playback
```

Useful variants:

```sh
cargo test -p musico_core                       # core crate, mobile.rs dispatch, stream_server, online stack
cargo test -p musico_recommender                # taste engine only
cargo test -p musico_core online::              # online module filter
cargo test -p musico_core mobile::              # JNI dispatcher & version tests
cargo test -p musico_playback --lib             # 27 hermetic playback/loopback unit tests
cargo test -p musico_core -- --list             # list collected tests
cargo test -p musico_core -- --nocapture        # show prints
```

Tests run in parallel by default. If a test touches process-global state (environment variables, the stream cache, a sled DB), it must isolate itself — see §6.

### Playback end-to-end

```sh
bash scripts/gen_test_tone.sh        # writes /tmp/musico-test-tone.wav
cargo test -p musico_playback
```

`MUSICO_TEST_WAV` points at a different file if needed. Alongside the 27 hermetic unit tests (`http_source.rs` loopback client, `queue.rs`, `eq.rs`, `decoder.rs`, `lib.rs`), the 4 end-to-end tests in `engine_e2e.rs` cover:

- play → position updates → `SongEnded`
- seek updates position
- pause is instant and holds position
- the tail is played out before `SongEnded`

If no audio device exists, `engine_e2e.rs` fails for environmental reasons, not because the decoder is broken. Use a desktop session or a virtual sink.

### Live network tests

Live tests are marked `#[ignore]` so a normal `cargo test` never hits the network. Run them explicitly:

```sh
cargo test -p musico_core live_ -- --ignored --nocapture
```

`MUSICO_LIVE_TESTS=1` is a documentation convention; **the code does not read it**. The `--ignored` flag is the actual gate. All **19** `live_*` tests in `musico_core`:

| Test | Checks |
|---|---|
| `live_search_and_radio` | Search results, continuation, cache hit < 100 ms, radio ≥ 3 tracks, seed excluded |
| `live_lyrics_durationless_no_foreign_song` | Durationless track lyric matching avoids foreign/wrong-length hits |
| `live_update_check` | GitHub `/releases/latest` payload parsing |
| `live_personalization_pipeline` | Personalized home/genre shelf pipeline |
| `live_resolve_non_saavn_track` | Non-Saavn YouTube track stream resolution |
| `live_cross_source_jpop` | Cross-source JioSaavn vs Direct ranking on J-pop titles |
| `live_album_detail` | Album header + at least five playable tracks |
| `live_genres` | ≥ 50 genre categories with browse IDs |
| `live_lyrics_fetch` | Mainstream song returns real unsynced lyrics |
| `live_home_feed` | ≥ 2 shelves and ≥ 10 items |
| `live_home_feed_never_empty` | Sparse profile backfill via charts |
| `live_batch_download_15_tracks` | 15-track batch, concurrency cap, all done |
| `live_cached_resolve_under_a_second` | Cold resolve then cache hit < 1 s |
| `live_search_suggestions` | Typeahead endpoint returns suggestions |
| `live_lrclib_synced_believer` | LRCLIB exact match + timed lines |
| `live_innertube_direct_resolve` | `InnerTubeDirectSource` direct stream extraction |
| `live_piped_resolve` | Piped direct-URL resolution |
| `live_invidious_resolve` | Invidious proxy resolution |
| `live_saavn_search_and_resolve` | JioSaavn search + direct download |

Requirements: network access, `yt-dlp` for download-heavy `yt-dlp` tests (`live_batch_download_15_tracks`, `live_cached_resolve_under_a_second`; Direct/Saavn tests do not need `yt-dlp`), and enough time (some tests have 240 s budgets). Live tests write to the real stream cache unless `MUSICO_CACHE_DIR` is set; use a temp cache if you care about a populated cache:

```sh
MUSICO_CACHE_DIR=/tmp/musico-live-cache \
  cargo test -p musico_core live_ -- --ignored --nocapture
```

## 3. Compose Multiplatform Kotlin checks

```sh
gradle -p android :app:compileDebugKotlin :desktopApp:compileKotlin :shared:desktopTest --console=plain
```

`android/shared/src/desktopTest/kotlin/io/github/aryan007h/musicoonline/SharedCoreTests.kt` runs **14** unit tests covering:

- `MusicoCoreCodec` envelope parsing & error propagation
- `DesktopPlatformPrefs` persistence
- `PlatformEqualizer` 5-band ↔ 10-band mapping (`mapFiveBandsToTenDb`)
- `ResolveFailurePolicy` auto-skip bounds (`maxAutoSkips = 3`) & `StreamResolvePolicy`
- `PlaylistImportResult` / `snapCacheCapMb` / count & downloads pluralization formatters
- `safeCornerDp` / `safePositiveScale` / `HorizontalScrollMath` / home personalization fallback

## 4. TypeScript tests (web preview engine)

The web/PWA demo engine lives in `src/engine/` and has its own tests (`package.json` runs the explicit three test files):

```sh
npm install
npm test        # tsx --test src/engine/recommender.test.ts src/engine/streamer.test.ts src/engine/features.test.ts
npm run build   # tsc --noEmit typecheck
```

`npm run build` and `npm run lint` are the same typecheck. It covers `server.ts`, `src/engine/*.ts`, and the Node types. It does **not** typecheck `frontend/` — that code is plain JS run in browsers.

## 5. Browser suites (web preview only)

These are end-to-end DOM assertions against the **unreleased web preview** (`server.ts` + `frontend/`), using its mock Bridge. They verify web-preview UI contracts, not Compose Multiplatform or Rust behavior.

### Setup

```sh
npm install
npm install --save-dev playwright
npx playwright install chrome
```

Start the preview on the port the suites expect:

```sh
PORT=8143 npm run dev
```

Run a suite in another terminal:

```sh
node scripts/browser_shell_test.mjs
node scripts/browser_home_test.mjs
node scripts/browser_library_test.mjs
node scripts/browser_detail_test.mjs
node scripts/browser_ctx_test.mjs
node scripts/browser_panel_test.mjs
node scripts/browser_storage_test.mjs
node scripts/browser_offline_test.mjs
node scripts/browser_typeahead_test.mjs
node scripts/genres_test.mjs
```

Always stop the server after a run; a stale server on 8143 makes the next run lie.

### WCAG contrast gate (web preview)

```sh
PORT=8145 npm run dev
node scripts/contrast_gate.mjs 8145
```

`scripts/contrast_gate.mjs` checks the 6 selectable web-preview palettes (`nebula`, `sakura`, `aurora`, `ocean`, `ember`, `mono`) × 2 themes (`light`, `dark`) and exits `1` on any pair below `4.5:1`. (It does not check the hidden `music` CSS palette or CMP's 5 dark palettes in `Palettes.kt`.)

## 6. Hermetic-test rules

These rules exist because earlier tests once deleted a user's real download cache (`#48`). Treat them as hard requirements.

1. **Never point a destructive test at the real cache.** Set `MUSICO_CACHE_DIR` to a `tempfile::tempdir()`, or use `TestCache`. `prune`/`clear`/`usage` really delete files.
2. **Never read or write the real sled DB.** Open a temp directory with `MusicRecommender::new(tmp.path())`.
3. **Do not depend on the developer's HOME or music folder.** Pass paths explicitly; use `tempfile` or the generated tone.
4. **No network in unit tests.** Put network tests in `#[ignore]` and name them `live_*`.
5. **Do not assert wall-clock speed on a loaded machine** unless the test is a live gate or uses a generous bound.
6. **Do not mutate process environment in parallel tests.** Serialize with a mutex or test the pure helper instead.
7. **Cover failure paths.** Prove bad URLs, missing fields, wrong content types, oversized bodies, or empty IDs are rejected.

## 7. What to test where

| Change | Minimum evidence |
|---|---|
| Pure Rust logic (parsing, policy, math) | Unit test with a fixture |
| JNI `dispatch()` command (`mobile.rs`) | Rust test in `musico_core/src/mobile.rs` + Kotlin codec/UI test in `SharedCoreTests.kt` |
| Network resolver / loopback server | Hermetic selection/validation/range test + `#[ignore]` live test |
| Cache/prune behavior | Temp-dir test proving bounds and cleanup |
| Settings field | `AppSettings` round-trip/partial-JSON test (`config.rs`) + Kotlin `CoreSettings` / UI check |
| Compose Multiplatform UI / logic | `gradle -p android :app:compileDebugKotlin :desktopApp:compileKotlin :shared:desktopTest` |
| Desktop audio / loopback source | `cargo test -p musico_playback --lib` (and full `cargo test -p musico_playback` with audio) |
| Web preview / TypeScript engine | `npm test`, `npm run build`, relevant `scripts/browser_*_test.mjs`, and `contrast_gate.mjs` |
| Release workflow | `workflow_dispatch` run before tagging |

## 8. CI behavior

`.github/workflows/ci.yml` runs on every push to `main` and every pull request:

1. **`rust-core`**: installs `libasound2-dev pkg-config`, runs `cargo test --workspace --exclude musico_playback`, and builds `cargo build --release -p musico_core --lib`.
2. **`compose-multiplatform`**: runs `gradle -p android :app:compileDebugKotlin :desktopApp:compileKotlin :shared:desktopTest --console=plain`.
3. **`web-engine`**: installs npm dependencies and runs `npm test && npm run build`.

`.github/workflows/release.yml` runs native release builds and packaging across `linux-desktop`, `macos-desktop`, `windows-desktop`, and `android-native`.

Not in CI: `musico_playback` device E2E tests, browser suites, the contrast gate, and live network tests.

## 9. Adding a test

- **Rust unit/integration:** put `#[cfg(test)] mod tests` at the bottom of the module, or add a file under `musico_playback/tests/` for engine E2E.
- **Live test:** add `#[test] #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]` and prefix the name with `live_`.
- **Kotlin CMP:** add test methods to `android/shared/src/desktopTest/kotlin/io/github/aryan007h/musicoonline/SharedCoreTests.kt`.
- **TypeScript:** add `src/engine/<area>.test.ts` and include it in `package.json`'s `"test"` script.
- **Browser (web preview):** copy the smallest existing suite, start the preview on `8143`, assert user-visible behavior, and clean up the server.
