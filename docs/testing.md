# Testing

Musico Online uses four test layers: hermetic Rust tests, opt-in live network
tests, TypeScript engine tests, and browser assertion suites. The CI workflow
runs the Rust suite on Linux; the release workflow repeats it on Linux, macOS,
and Windows. The other layers are local tools.

## 1. Test inventory

| Layer | Count | Runs in CI | Needs |
|---|---|---|---|
| Rust workspace | **189** collected | Yes (minus playback) | Rust toolchain |
| — `src-tauri` | 109 (13 `#[ignore]` live) | Yes, live skipped | Rust |
| — `musico_recommender` | 62 unit + 1 doctest | Yes | Rust |
| — `musico_playback` | 16 + 1 doctest | **No** (excluded) | Audio device, test WAV |
| TypeScript engine | **20** | No | Node + `npm install` |
| Browser suites | 10 files, ~151 assertions | No | Web preview + Playwright + Chrome |
| Contrast gate | 6 palettes × 2 themes | No | Web preview + Playwright + Chrome |

CI's command is:

```sh
cargo test --workspace --exclude musico_playback
```

That collects **172** tests: 109 app tests plus 63 recommender tests (62 unit +
1 doctest). Thirteen of the app tests are `#[ignore]` live tests, so 159 run by
default. The full workspace adds 17 playback tests (16 + 1 doctest) for 189.

Why playback is excluded: `musico_playback/tests/engine_e2e.rs` opens a real
audio device and plays a real file. GitHub runners have neither. The tests are
not deleted; run them locally.

## 2. Rust tests

### Standard run

```sh
cargo test --workspace --exclude musico_playback
```

Useful variants:

```sh
cargo test -p musico-online                     # app crate only
cargo test -p musico_recommender                # taste engine only
cargo test -p musico-online online::            # module filter
cargo test -p musico-online -- --list           # list collected tests
cargo test -p musico-online -- --nocapture      # show prints
```

Tests run in parallel by default. If a test touches process-global state
(environment variables, the stream cache, a sled DB), it must isolate itself —
see §5.

### Playback end-to-end

```sh
bash scripts/gen_test_tone.sh        # writes /tmp/musico-test-tone.wav
cargo test -p musico_playback
```

`MUSICO_TEST_WAV` points at a different file if needed. The four end-to-end
tests cover:

- play → position updates → `SongEnded`
- seek updates position
- pause is instant and holds position
- the tail is played out before `SongEnded`

If no audio device exists, these fail for environmental reasons, not because
the decoder is broken. Use a desktop session or a virtual sink.

### Live network tests

Live tests are marked `#[ignore]` so a normal `cargo test` never hits the
network. Run them explicitly:

```sh
cargo test -p musico-online live_ -- --ignored --nocapture
```

`MUSICO_LIVE_TESTS=1` is a documentation convention; **the code does not read
it**. The `--ignored` flag is the actual gate. The 13 live tests are:

| Test | Checks |
|---|---|
| `live_search_and_radio` | Search results, continuation, cache hit < 100 ms, radio ≥ 3 tracks, seed excluded |
| `live_album_detail` | Album header + at least five playable tracks |
| `live_genres` | ≥ 50 genre categories with browse ids |
| `live_lyrics_fetch` | Mainstream song returns real unsynced lyrics |
| `live_home_feed` | ≥ 2 shelves and ≥ 10 items |
| `live_home_feed_never_empty` | Sparse profile backfill via charts |
| `live_batch_download_15_tracks` | 15-track batch, concurrency cap, all done |
| `live_cached_resolve_under_a_second` | Cold resolve then cache hit < 1 s |
| `live_search_suggestions` | Typeahead endpoint returns suggestions |
| `live_lrclib_synced_believer` | LRCLIB exact match + timed lines |
| `live_piped_resolve` | Piped direct-URL resolution |
| `live_invidious_resolve` | Invidious proxy resolution |
| `live_saavn_search_and_resolve` | JioSaavn search + direct download |

Requirements: network access, `yt-dlp` for download tests, and enough time
(some tests have 240 s budgets). Live tests write to the real stream cache
unless `MUSICO_CACHE_DIR` is set; most clean up their own files, but a killed
run can leave residue. Use a temp cache if you care about a populated cache:

```sh
MUSICO_CACHE_DIR=/tmp/musico-live-cache \
  cargo test -p musico-online live_ -- --ignored --nocapture
```

## 3. TypeScript tests

The web/PWA engine lives in `src/engine/` and has its own tests:

```sh
npm install
npm test        # tsx --test src/engine/*.test.ts
npm run build   # tsc --noEmit typecheck
```

The 20 tests are split across three files:

`recommender.test.ts` and `streamer.test.ts` (10) cover:

- harmonic/Camelot transition scoring
- vector standardization without NaN
- multi-modal taste personas (local + online)
- bandit weight updates maintaining a unit sum
- predictive prefetch planning
- quality/resolution URL mapping
- cross-source match heuristic
- endpoint health ranking
- hedged source racing
- progressive stream playability threshold

`features.test.ts` (10, under the "Musico 10 Standout Capabilities Engine"
suite) covers:

- beat-aligned Auto-DJ crossfade clamping and bar alignment
- Taste DNA persona re-ranking
- stem-separation mode gains
- EBU R128 loudness normalization and true-peak guard
- Sonic Bridge pathfinding
- AutoEQ curves and tube-warmth harmonics
- local Jam Session room/queue behavior
- word-level lyric annotation assembly
- daypart/time-capsule selection
- command-palette query parsing

`npm run build` and `npm run lint` are the same typecheck. It covers
`server.ts`, `src/engine/*.ts`, and the Node types. It does **not** typecheck
`frontend/` — that code is plain JS loaded directly by the webview.

## 4. Browser suites

These are end-to-end DOM assertions against the **web preview**, using its mock
Bridge. They verify UI contracts (navigation, routes, event wiring, ARIA,
selectors, queue behavior), not Rust backend behavior.

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

Each script prints `PASS`/`FAIL` lines plus any `pageerror` messages, and exits
non-zero if an assertion or page error occurred. Always stop the server after a
run; a stale server on 8143 makes the next run lie.

| Suite | Focus |
|---|---|
| `browser_shell_test.mjs` | Sidebar/topbar chrome, routes, LCD transport, seek casing, fullscreen vinyl, compact layout, macOS/Windows chrome stubs |
| `browser_home_test.mjs` | Home shelves, card playback, navigation, feed caching |
| `browser_library_test.mjs` | Library tabs, rows, A–Z, search, favorites |
| `browser_detail_test.mjs` | Album/artist/playlist detail, tracklist, play-all, queue-all, download-all |
| `browser_ctx_test.mjs` | Context menus: play next/last, download, playlist, library, radio, queue remove |
| `browser_panel_test.mjs` | Playing Next panel, autoplay toggle sync, keyboard activation, queue operations |
| `browser_storage_test.mjs` | Storage card, cap slider, clear cache, batch progress/retry/cancel |
| `browser_offline_test.mjs` | Airplane mode fast-fail, cached views, offline search behavior |
| `browser_typeahead_test.mjs` | Debounced suggestions, keyboard navigation, silent failure |
| `genres_test.mjs` | Genre grid, lazy load, detail navigation, cache reuse |

The agent workflow serializes suites on port 8143 with a "suite lock" in
`agentchat.md`. If multiple agents or terminals run browsers, take the lock or
use a different `PORT` and update the URL in the script.

### WCAG contrast gate

The contrast gate reads computed colors from the live page and checks
text-on-surface pairs in every palette × theme combination:

```sh
PORT=8145 npm run dev
node scripts/contrast_gate.mjs 8145
```

It exits 1 on any pair below 4.5:1. The default port is 8145 so it can run
alongside a suite on 8143. It is a UI gate, not part of `cargo test`.

## 5. Hermetic-test rules

These rules exist because earlier tests once deleted a user's real download
cache (`#48`). Treat them as hard requirements.

1. **Never point a destructive test at the real cache.** Set
   `MUSICO_CACHE_DIR` to a `tempfile::tempdir()`, or use the `TestCache` helper
   in `online/stream.rs` test support. `prune`/`clear`/`usage` really delete
   files.
2. **Never read or write the real sled DB.** Open a temp directory with
   `MusicRecommender::new(tmp.path())`.
3. **Do not depend on the developer's HOME or music folder.** Pass paths
   explicitly; use `tempfile` or the generated tone.
4. **No network in unit tests.** Put network tests in `#[ignore]` and name them
   `live_*`.
5. **Do not assert wall-clock speed on a loaded machine** unless the test is a
   live gate or uses a generous bound. Prefer pure functions and fixtures.
6. **Do not mutate process environment in parallel tests.** If a test needs an
   env var, serialize it with a mutex or test the pure helper instead.
7. **Cover failure paths.** A resolver test should prove the bad URL, missing
   field, wrong content type, oversized body, or empty id is rejected — not just
   the happy path.

## 6. What to test where

| Change | Minimum evidence |
|---|---|
| Pure Rust logic (parsing, policy, math) | Unit test with a fixture |
| IPC command | Unit test where possible; browser assertion for the UI contract |
| Network resolver | Hermetic selection/validation test + one `#[ignore]` live test |
| Cache/prune behavior | Temp-dir test proving bounds and cleanup |
| Settings field | Partial-JSON/default test + settings UI assertion |
| Frontend view | Browser suite assertion in the relevant file |
| CSS theme/contrast | Contrast gate |
| Playback engine | `cargo test -p musico_playback` on a machine with audio |
| Release workflow | `workflow_dispatch` run before tagging |

## 7. CI behavior

`.github/workflows/ci.yml` runs on every push to `main` and every pull request:

1. Checkout.
2. Install Rust stable + cache.
3. Install Linux Tauri dependencies.
4. `cargo test --workspace --exclude musico_playback`.

`.github/workflows/release.yml` repeats that test step on Linux, macOS, and
Windows before building bundles. The macOS job runs natively on arm64, which
also type-checks `cfg(target_os = "macos")` code; the Windows job does the same
for `cfg(target_os = "windows")`.

Not in CI: browser suites, the contrast gate, TypeScript tests, live tests, and
Android builds. Run them locally when your change touches those areas.

## 8. Adding a test

- **Rust unit/integration:** put `#[cfg(test)] mod tests` at the bottom of the
  module, or add a file under `musico_playback/tests/` for engine e2e.
- **Live test:** add `#[test] #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]`
  and prefix the name with `live_`.
- **TypeScript:** add `src/engine/<area>.test.ts` and include it in the
  `package.json` test script if it is a new file.
- **Browser:** copy the smallest existing suite, start the preview on 8143, and
  assert user-visible behavior. Clean up listeners and close the browser.

A test that cannot fail is not a test. Prefer an assertion that would catch the
regression you are fixing over a smoke assertion that only proves the page
loaded.
