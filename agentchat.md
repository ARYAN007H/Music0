# agentchat.md — Musico multi-agent coordination hub

Multiple AI agents work on Musico Online **at the same time**. Chats are
forked and cannot see each other — **this file is the only message bus
between agents.** Anything you want the team to know goes in a log entry
here.

**Round 2 (2026-11-01) mission: multi-source playback** — near-instant
stream resolution via a Piped/Invidious direct-URL fast path (#53) plus a
JioSaavn catalog source. Task background: issue #53 (closed by PR #A) and
the JioSaavn issue (linked in T4).

## How the team works

- **agent-1 is the lead.** It orchestrates (reviews logs, guides, assigns,
  reviews PRs) AND implements its own task this round. It remains the ONLY
  agent allowed to run git state operations **in the shared tree**
  (`/home/aryan/Documents/projects/Musico-online`) — merges, branch ops,
  stashes.
- **Workers execute their one claimed task end-to-end** in the worktree
  assigned to them (see the task): code → tests → commit → push → PR →
  address review → log DONE. Inside their OWN worktree a worker may run git
  add/commit/push on its own branch only — never touch the shared tree's
  git state, never checkout branches elsewhere.
- When blocked or unsure, log a QUESTION entry and keep moving on a
  non-conflicting part of your task.
- The user (Aryan) relays between chats and makes product decisions.

## Joining — do this FIRST, before touching any code

1. Pick a name: `agent-<word>` where `<word>` is any random word of your
   choice **not already in the Roster** (agent-1 is reserved for the lead).
2. Append a log entry: *"I'm agent-\<word\>, claiming task T\<n\>: \<one
   line\>"*.
3. Flip your task's status line to `IN PROGRESS — agent-<word>` in Tasks.
4. One task per agent at a time. Finish or hand it back before another.

## The rules (breaking these breaks another agent's work)

- **Work only in your assigned worktree.** The shared tree belongs to
  agent-1 this round. Never run git checkout/stash/reset in the shared tree.
- **Browser suites serialize on port 8143.** Before running any
  `scripts/browser_*_test.mjs` suite, log `SUITE LOCK taken`; after the run
  (server killed), log `SUITE LOCK released`. One agent at a time.
- `src-tauri/src/commands.rs` is a shared hotspot. Append-only: add your
  command/merge code, never reorder or reformat existing code.
- Edit the smallest region that does the job — no whole-file rewrites, no
  drive-by refactors, no reformatting. `parse.rs` is the only
  response-shape-drift-sensitive file; keep it that way.
- If two tasks need the same file region, log a `CONFLICT` entry and wait
  for agent-1.
- Frontend `?v=` bumps in `frontend/index.html` only if you change frontend
  files.
- The chrome restructure (bottom pill vs mini-dock card) stays **BLOCKED —
  awaiting a decision from Aryan.**

## Logging — mandatory, every meaningful step

Append entries at the **bottom** of the Log section (newest last). Get a
timestamp with `date -u +"%Y-%m-%d %H:%M"`. Log when you: claim a task,
finish each meaningful chunk (files + what changed), get blocked (QUESTION),
and finish (DONE + how to verify).

Entry format — one block per event:

```
### 2026-11-01 09:00 UTC — agent-<word>
- STATUS: CLAIMED | WORKING | BLOCKED | QUESTION | CONFLICT | DONE | SUITE LOCK taken/released
- TASK: T3
- FILES: src-tauri/src/online/sources.rs
- WHAT: concrete one/two lines — what changed, what's next
```

**Retention: only the 25 most recent log entries stay saved.** After
appending, count the `### ` entry headers; if more than 25, delete the
oldest entries from the top until 25 remain. Only trim by this
oldest-first rule — never delete a specific agent's entry otherwise.

## Roster

| Agent | Role | Status |
|---|---|---|
| **Aryan** | coordinator — product owner: all product decisions, merges & releases; relays between chats | active |
| agent-1 | lead — orchestration + T3 (stream source fast path) | active |
| agent-ember | worker — T4 (JioSaavn) + #61 lyrics — both landed | round complete |
| agent-falcon | worker — T10 (macOS release support) | active |
| agent-cinder | round-1 worker — retired (round superseded) | inactive |
| (unknown) | pre-existing bug-fix agent — retired after PR #52 landed | inactive |
| agent-prism | worker — Round 3 stability & polish (T11–T19 + sweep) | round 3 done (T16 approved → T23) |
| agent-solstice | worker — Round 4 production-grade polish, fixes & playlist import (T20–T24) | active |

## Tasks — round 4 (2026-11-22, production-grade polish & import)

- **T20** — Fix gradient cutoffs (`Modifier.musicoWash`), overhaul Aurora & Rain backdrops, and floating squircle pill dock on Desktop: `DONE (verify: inspect Home, Search, Radio, Library, Detail, Player, Lyrics Studio across all 5 palettes & all 5 backdrops on Desktop & Android at multiple window sizes — zero horizontal gradient cutoffs, volumetric Aurora rays, rain-on-glass droplets/rivulets, floating squircle pill dock on Desktop)`
- **T21** — Uniform M3 Expressive polish audit across all 12 screens (Android + Desktop): `DONE (verify: inspect all 12 screens in Android + Desktop across all 5 palettes — complete 5-rung tonal ladder, matching skeletons on Search/Radio/Detail, inline Retry on Detail, flat icon tiles in Settings/Profile, unified headline/titleLarge/overline typography)`
- **T22** — Correctness odds & ends (English shelf banner, version sync 0.9.6-cmp, CSPRNG stream tokens, desktop resize ghosting, cold-start playback latency): `DONE (verify: cargo test -p musico-online stream_server + cargo test -p musico-online version_newer + ./gradlew :shared:desktopTest + tap uncached track on cold launch + inspect Home onboarding banner, Settings About version, and Desktop 840dp resize)`
- **T23** — Playlist import (URL + file import, minimal v1): `DONE (verify: cargo test -p musico-online playlist_import + ./gradlew :shared:desktopTest + test CSV/JSON/M3U file import & public Spotify/Apple Music URL import from Settings → Library and Onboarding)`
- **T24** — Release-readiness docs (`CHANGELOG.md` 0.9.6, `README.md` Desktop CMP section): `DONE (verify: inspect CHANGELOG.md [0.9.6] Unreleased section & README.md Desktop Compose App / MUSICO_NATIVE_LIB docs; version kept at 0.9.6-cmp without tagging)`

## Tasks — round 3 (2026-11-21, stability & polish)

- **T11** — Streaming playback + honest resolve errors: `DONE (verify: cargo test -p musico-online stream_server + ./gradlew :shared:desktopTest + device play uncached/broken track)`
- **T12** — Downloads list raw IDs enrichment + sidecar metadata: `DONE (verify: cargo test -p musico-online sidecar + downloads_list_uses_sidecar + parse_video_details_meta)`
- **T13** — Unified edge-to-edge insets across all screens: `DONE (verify: inspect top-edge on Home/Search/Radio/Library/Detail/PlaylistDetail/NowPlaying/Settings/EQ/Onboarding/Profile on phone + desktop)`
- **T14** — Stream-cache slider smooth local drag + 256 MB snap: `DONE (verify: ./gradlew :shared:desktopTest + drag stream-cache slider smoothly on phone/desktop)`
- **T15** — Equalizer effectiveness (Android audiofx + Desktop DSP persistence): `DONE (verify: cargo test -p musico-playback eq::tests + ./gradlew :shared:desktopTest + logcat Music0EQ on Android + Desktop EQ relaunch)`
- **T16** — Playlist import entry points (proposal first, await Aryan OK): `BLOCKED — awaiting Aryan's OK (see QUESTION in Log)`
- **T17** — Copy fixes (Smart Mixes pluralization + Downloads pinned card): `DONE (verify: ./gradlew :shared:desktopTest + inspect Smart Mixes & Downloads card)`
- **T18** — Negative corner size crash clamp + dynamic shape/size audit: `DONE (verify: ./gradlew :shared:desktopTest + rapid open/close Player & tab switches on Desktop/Android)`
- **T19** — Desktop general sweep (shortcuts, resize, HiDPI, core banner): `DONE (verify: ./gradlew :shared:desktopTest + desktop resize <900px, type in Search/Rename, check shortcuts & core-warning banner)`

## Tasks — round 2 (2026-11-01)

Statuses: `OPEN` → `IN PROGRESS — agent-<word>` → `DONE (verify: …)`.

### T3 — Piped/Invidious direct-URL fast path (#53) — IN PROGRESS — agent-1
**Owner: agent-1.** Works in the shared tree on branch
`feat/stream-source-fallback` → PR #A into `main`, closes #53.

New `src-tauri/src/online/sources.rs`: instance pools (Piped + Invidious,
env-overridable), best-m4a stream selection from Piped `audioStreams`,
Invidious `latest_version?id=…&itag=140&local=true` proxy fetch, deadline-
bounded failover (injectable source list, unit-testable without network),
stream-to-`.part` → atomic rename → size verify → prune, reusing the
existing polish flow. Integrated in `stream.rs::resolve_with` after the
cache check, before yt-dlp; `yt_dlp_available()` moves below the fast path
so playback no longer hard-requires yt-dlp. Hermetic tests + `#[ignore]`
live tests. README + ROADMAP updated.

### T6 — Search typeahead (#59) — DONE — PR #68 (feat/search-typeahead)
**Owner: agent-ember** (round 3, per agent-1's 14:20 assignment). Verify the
InnerTube `music/get_search_suggestions` endpoint LIVE first (TO-VERIFY,
ROADMAP line 85), then backend command + 200 ms debounce typeahead in the
search view (↑/↓/Enter/Escape); API failure degrades silently. Fallback if
dead: n-grams over recent searches + library. Worktree:
`~/.cache/musico-worktrees/typeahead` (the old saavn worktree was removed —
recreated off origin/main fd3bbc3, branch feat/search-typeahead).

### T4 — JioSaavn catalog source — DONE — merged as PR #56
**Owner: the forked worker (register your name, then claim).** Works in the
dedicated worktree `~/.cache/musico-worktrees/saavn` (branch
`feat/saavn-source`, created off main by agent-1) → its own issue → PR #B
into `main` (off main, NOT stacked; conflicts are agent-1's to resolve).

**CANONICAL HUB: read THIS file at
`/home/aryan/Documents/projects/Musico-online/agentchat.md` — your
worktree's copy is a stale committed snapshot.** Spec: GitHub issue #54
(`gh issue view 54`). Summary:
1. **API (APPROVED by agent-1 — native pivot, see ANSWER in Log):**
   JioSaavn's own web API `https://www.jiosaavn.com/api.php`
   (`__call=search.getResults` / `song.getDetails`,
   `_format=json&_marker=0&api_version=4&ctx=web6dot0`). Download URLs come
   from `more_info.encrypted_media_url` — DES-ECB, static key `"38346591"`,
   base64 ciphertext, PKCS7 — yielding `aac.saavncdn.com/…_96.mp4` (swap
   `_96`→`_320` for 320 kbps). agent-ember verified live: Believer → HTTP
   200 audio/mp4, 8.5 MB / 204 s. Base URL env-overridable
   (`MUSICO_SAAVN_API`). saavn.dev is dead (NXDOMAIN — wrapper
   discontinued); sumit.co noted on #54 only as an emergency fallback.
2. **`src-tauri/src/online/saavn.rs`**: tolerant serde parse of
   `/api/search/songs` results; map into `OnlineTrack` with a new
   `#[serde(default)] pub source: String` field (`""` = yt, `"saavn"` =
   saavn). `OnlineTrack::song_id()` becomes source-aware (`yt:{id}` vs
   `saavn:{id}`) — update every struct literal the compiler points at
   (parse.rs, models.rs, tests). ALL `id.starts_with("yt:")` checks
   (event_bridge radio advance, lyrics, suggested) must keep working
   unchanged — saavn ids skip them by namespace.
3. **Resolve**: `resolve_saavn(id)` — fetch song details by id, pick the
   `320kbps` `downloadUrl` (fall back to lower quality), stream to cache
   file stem **`saavn:{id}`** (colon — impossible in YT ids, so
   `cached_file()`'s `{key}.` prefix matching can never cross namespaces;
   Linux-target app, document the constraint). Same
   verify(`MIN_FILE_BYTES`)/prune/polish flow: reuse `CoverJob` (make it
   `pub(crate)` in stream.rs), `probe_duration_secs`, `to_song_info`.
   Dedicated reqwest client, 3 s timeout, API failure = no saavn results /
   honest resolve error. No yt-dlp involvement.
4. **Dispatch**: top of `stream.rs::resolve_track` —
   `if track.source == "saavn" { return super::saavn::resolve_track(track, interactive); }`.
   Also allowlist `saavncdn.com` (suffix match) in stream.rs
   `is_safe_art_url` so saavn covers work. These are the ONLY stream.rs
   touches — log them (agent-1 is editing `resolve_with` in the same file;
   different regions, do not reformat).
5. **Search merge**: in `commands.rs::online_search` (first page only —
   `continuation.is_none()`), append ≤10 saavn songs after YT results,
   `cached` badge via the `saavn:{id}` cache key. YT search itself and its
   TTL cache stay untouched — saavn failure must never poison them.
6. **Out of scope** (note in PR): saavn radio/suggested, saavn lyrics,
   saavn browse pages, frontend source badge.
7. **Tests**: fixture parse tests (malformed JSON, missing fields, missing
   320kbps → lower quality fallback), resolve-to-tempdir via `TestCache`
   (`stream.rs` test module — make it `pub(crate)` under `cfg(test)` if
   needed, or ask agent-1), one `#[ignore]` live test gated
   `MUSICO_LIVE_TESTS=1`.

**Environment (your worktree):** run
`export PATH="$HOME/.cargo/bin:$PATH"`; keep `CARGO_TARGET_DIR` default
(in-worktree `target/`); run `bash scripts/gen_test_tone.sh` before
`cargo test` (engine e2e fixtures); browser suites need
`ln -sfn ~/.npm/_npx/e41f203b7505f1fb/node_modules node_modules` (already
done for you) and the SUITE LOCK rule above.
**Before pushing:** `cargo test --workspace` green, `cargo clippy` no NEW
warnings, all 8 browser suites green.

### T10 — macOS release support — DONE — PR #84 (agent-falcon, pending agent-1 review)
**Owner: agent-falcon** (direct assignment from Aryan, 2026-11-01; design
clarified by Aryan 2026-11-02 — see Log). Works in the worktree
`~/.cache/musico-worktrees/mac-release` (branch `feat/mac-release`, created
off origin/main 2b86b70) → PR into `main`.

1. **Window** (`tauri.conf.json`): `titleBarStyle: "Overlay"` +
   `hiddenTitle: true` + `trafficLightPosition {x:20,y:20}` — macOS-only
   fields, silently ignored on Linux/Windows.
2. **Native app menu** (`main.rs`, `cfg(target_os = "macos")`):
   App/Edit/View/Window submenus from predefined roles — WITHOUT an Edit
   submenu, Cmd+C/V/A never reach the WKWebView on macOS, so this is
   functional, not chrome. Windows: `set_decorations(false)` at runtime
   (drawn controls replace the title bar). commands.rs NOT touched.
3. **CI** (`release.yml`): new `macos` job (macos-latest, universal
   apple-darwin) — runs `cargo test --workspace` natively (validates the
   cfg(macos) code), builds unsigned `.dmg` + `.app.tar.gz`; publish job
   collects both platforms; Gatekeeper note in the release body.
4. **Frontend — NO extra title bar on ANY platform (Aryan)**: #app grid →
   full-height sidebar (`"sidebar topbar" / "sidebar content"`); the window
   controls live in the sidebar's top-left, above the "Music" brand:
   `native.css` + shell.js `WindowChrome` strip fixed there — mac = native
   traffic lights (config position) over a click-through drag strip;
   Windows = drawn min/max/close wired to the Tauri window API
   (capabilities += window minimize/toggle-maximize/close); Linux = system
   decorations kept, strip hidden. SF Pro system font stack on mac.
5. **Docs**: README macOS/Windows section (rustup targets, artifacts,
   Gatekeeper, resize note), ROADMAP line. ?v= bump per house rule.

## Backlog — unassigned, do NOT self-assign (agent-1 hands these out)

- Search typeahead (round-1 T1, never landed — InnerTube suggestions
  endpoint TO-VERIFY, ROADMAP line 85)
- Player quick wins: artist link from expanded player, volume pill
  (round-1 T2 leftovers); LRCLIB synced lyrics (highest-impact player
  upgrade)
- P0 #1 first-run onboarding + YTM charts shelf on Home
- Local sled playlists with add-from-anywhere context menu
- "Because you liked X" shelf headers; per-track download state icons
- P2: view transitions, skeletons, `/` `l` `q` shortcuts, track title in
  window title
- BLOCKED on Aryan: chrome proposal (pill vs card decision)

## agent-onyx — 2026-11-12
WORKING: triaging independent pre-merge security review (agent verdict: FIX-FIRST, 0 P0 / 5 P1 / 12 P2)
DONE:
- P1-1 prefetchUpcoming was DEAD CODE (never called; changelog claimed it) → wired into resolveAndPlay success; new `{"background":true}` flag on track.resolve routes it to the core's BG budget (mobile.rs)
- P1-2 stale-resolve race → playGeneration token + resolveJob cancel; failures only act when the failed track is still at the playhead
- P1-3 offline queue wipe → no auto-skip when offline/every-track-fails + MAX_AUTO_SKIPS=3 cap with honest message
- P1-4 updater integrity → sha256 digest from GitHub API verified post-download (mobile.rs passes assetDigest), size ceiling mid-stream, real cancellation (ensureActive), ActivityNotFoundException guard
- P1-5 year-as-duration → parse_duration_colon (colon shape required) at both subtitle-tail sites + test `parse_duration_colon_rejects_bare_numbers`
- P2 batch: shelf durations passed, EQ bars stop ticking when paused, settings.save mutex, initNative idempotent+catch_unwind, OOM → throw_new, videoId charset gate, loadMore breadcrumbs, notes per-line cap, MusicoApp toast keyed on errorSeq, release.yml derives versionCode/Name from tag, offline saves on BG budget, LRCLIB rung-1.5 (api_get without duration behind word-set gate)
- Verified: 105 lib tests, android-target check, gradle compileReleaseKotlin all green
NEXT: commit+push → CI → PR → merge main → tag v0.9.1 → v0.9.2 for the on-phone updater E2E test

## agent-onyx — 2026-11-12 (2)
WORKING: 0.9.3 release — update experience + shell polish
DONE:
- E2E updater PROVEN on-device (Android 17): pose build 0.9.1 (MUSICO_VERSION_OVERRIDE, versionCode unchanged → no downgrade trip) → every-launch check → banner + system notification (id=41, channel app_updates) → tap → 40 MB download → sha256 digest verified → "Permission required" unknown-sources gate (one-time) → "Update this app?" → Google Play Protect scan → installed 0.9.2-native
- Update banner → floating popup (TopCenter, statusBarsPadding, shadowElevation, live MB progress; no page push / clock overlap)
- Downloaded-APK reuse on retry (verifyApk against digest/size → skip re-download after permission round-trip)
- Floating pills fix (from #117) + notifications confirmed working on-device
- release.yml: dispatch inputs now override versionCode/versionName (test rig)
NEXT: 0.9.3 tag → release publish → make_latest → phone updates itself 0.9.2→0.9.3 through the real flow

### 2026-11-21 16:12 UTC — agent-prism
- STATUS: DONE
- TASK: T12
- FILES: src-tauri/src/online/{stream,sources,saavn,mod}.rs, src-tauri/src/mobile.rs, android/shared/src/commonMain/.../MusicViewModel.kt
- WHAT: Added companion `{stem}.meta.json` sidecars written on download/resolve completion, excluded from audio cache scans/listings, and pruned alongside audio; added `library.downloads.enrich` (rate-limited 250 ms/item, max 15/pass, skipped offline, tracked in sled `mobile:enrich_attempted`) using InnerTube `/player` `videoDetails` + oEmbed / JioSaavn `song.getDetails`; wired non-blocking background enrichment in `MusicViewModel.refreshDownloadsInternal`; added 3 hermetic Rust tests (`sidecar_metadata_roundtrips_and_prunes_with_audio`, `parse_video_details_meta_extracts_title_artist_and_duration`, `downloads_list_uses_sidecar_and_falls_back_to_stem_offline`). Verify: `cargo test -p musico-online` + open Downloads online & in Airplane Mode.

### 2026-11-21 16:13 UTC — agent-prism
- STATUS: CLAIMED
- TASK: T13
- FILES: android/shared/src/commonMain/.../{MusicoApp,home/HomeScreen,search/SearchScreen,radio/RadioScreen,library/LibraryScreen,detail/DetailScreen,library/PlaylistDetailScreen,player/NowPlayingScreen,settings/SettingsScreen,settings/EqScreen,onboarding/OnboardingScreen,profile/ProfileScreen}.kt
- WHAT: Claiming task T13: unify edge-to-edge status-bar/cutout inset strategy in `MusicoApp` and audit every screen so ambient washes bleed full-bleed behind the status bar, content never underlaps cutouts, and no screen double-pads.

### 2026-11-21 16:20 UTC — agent-prism
- STATUS: DONE
- TASK: T13
- FILES: android/shared/src/commonMain/.../{components/Common,MusicoApp,home/HomeScreen,search/SearchScreen,radio/RadioScreen,library/LibraryScreen,detail/DetailScreen,library/PlaylistDetailScreen,player/NowPlayingScreen,settings/SettingsScreen,settings/EqScreen,onboarding/OnboardingScreen,profile/ProfileScreen}.kt
- WHAT: Added centralized `@Composable fun Modifier.musicoTopInset()` (`WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Top)`), removed outer compact `statusBarsPadding()` from `MusicoApp` so `AmbientTopWash` bleeds full-bleed behind the status bar on all primary tabs, replaced all ad-hoc `statusBarsPadding()` calls across every screen + floating update banner (and added `musicoTopInset()` + `navigationBarsPadding()` to widescreen NowPlaying, DetailScreen, and EqScreen). Verify: open every screen on notched Android phone + Desktop; no black status-bar strip, no cutout underlap, no double padding.

### 2026-11-21 16:21 UTC — agent-prism
- STATUS: CLAIMED
- TASK: T14
- FILES: android/shared/src/commonMain/.../settings/SettingsScreen.kt, src-tauri/src/mobile.rs
- WHAT: Claiming task T14: fix stream-cache slider local drag state so the thumb glides smoothly, updates the live label, and snaps/persists on 256 MB steps in `onValueChangeFinished`; wire persisted `online_cache_mb` cap into `stream::set_cache_cap_override_mb` on init and save.

### 2026-11-21 16:28 UTC — agent-prism
- STATUS: DONE
- TASK: T14
- FILES: android/shared/src/commonMain/.../settings/SettingsScreen.kt, android/shared/src/commonMain/.../ui/MusicViewModel.kt, src-tauri/src/mobile.rs
- WHAT: Replaced persisted-value binding on the Settings stream-cache Slider with local `dragMb`/`dragging` state, live 256 MB snapped label (`snapCacheCapMb`), and single persist in `onValueChangeFinished` (with optimistic `coreSettingsCacheMb` update in `MusicViewModel.setCacheCap` to eliminate release snap-back); wired `stream::set_cache_cap_override_mb` on `init_core` and `settings.save` in `mobile.rs` so the saved cap is honored live. Verify: drag slider smoothly in Settings on Android & Desktop, release anywhere, confirm cap lands on 256 MB step and persists across relaunch.

### 2026-11-21 16:29 UTC — agent-prism
- STATUS: CLAIMED
- TASK: T15
- FILES: android/shared/src/androidMain/.../player/{MobileEqualizer,PlaybackService}.kt, android/shared/src/commonMain/.../settings/EqScreen.kt, android/shared/src/desktopMain/.../platform/{DesktopPlatform,DesktopPlayback}.kt, android/desktopApp/.../Main.kt, src-tauri/src/mobile.rs, crates/musico_playback/src/eq.rs
- WHAT: Claiming task T15: instrument & fix Android AudioFX equalizer (session attach on init/change/track-transition, band read-back logs, auto-enable on non-Flat preset, no prefs clobber on re-attach), persist & serialize Desktop EQ over JNI + reapply on playback start, and add Rust DSP verification test in `musico_playback`.

### 2026-11-21 16:36 UTC — agent-prism
- STATUS: DONE
- TASK: T15
- FILES: android/shared/src/androidMain/.../player/{MobileEqualizer,PlaybackService}.kt, android/shared/src/commonMain/.../settings/EqScreen.kt, android/shared/src/desktopMain/.../platform/{DesktopPlatform,DesktopPlayback}.kt, android/desktopApp/.../Main.kt, src-tauri/src/mobile.rs, musico_playback/src/eq.rs
- WHAT: Android: instrumented `MobileEqualizer` with `Log.i("Music0EQ", ...)` (session ID, band range, `setEnabled`/`setBandLevel` + hardware `getBandLevel` read-back, `hasControl()`, `BassBoost` read-back); fixed `PlaybackService` to attach initial `player.audioSessionId` and call `MobileEqualizer.ensureApplied(...)` on `onMediaItemTransition` and `onIsPlayingChanged(true)`; fixed `attached` flag, stopped re-reading SharedPreferences on session re-attach, prioritized AudioFX (1→0), auto-enabled EQ on non-Flat presets, and fixed `EqScreen` `DisposableEffect` listener sync. Desktop: persisted EQ in `DesktopPlatformPrefs` via `PlatformEqualizer.init(prefs)` in `Main.kt`, serialized `player.set_eq` JNI pushes through a conflated `Channel` so rapid drags never race out-of-order, reapplied EQ in `DesktopPlayback.playFile`, logged `desktop_set_eq` in `mobile.rs`, and added 2 hermetic Rust DSP tests in `musico_playback/src/eq.rs` (`eq_disabled_passes_samples_unchanged`, `eq_bass_boost_measurably_shapes_low_freq_output`). Verify: `cargo test -p musico-playback eq::tests`, `./gradlew :shared:desktopTest`, check `adb logcat -s Music0EQ` while switching presets/tracks on Android, and toggle/relaunch EQ on Desktop.

### 2026-11-21 16:37 UTC — agent-prism
- STATUS: QUESTION
- TASK: T16
- FILES: (proposal only — awaiting Aryan's OK before writing implementation code)
- WHAT: Proposal for "Import playlist" entry points (Onboarding + Settings → Library) without gated Spotify/Apple OAuth APIs:
  (1) **Two zero-OAuth ingestion paths in one modal/sheet**:
      - **Exported file import** (Android SAF `OpenDocument` / Desktop `FileDialog`): parse Exportify / TuneMyMusic / Soundiiz CSV (`Track Name, Artist Name(s), Album Name, Duration`), Spotify/Music0 JSON, and M3U/M3U8.
      - **Public playlist URL / Android Share-sheet (`ACTION_SEND`)**: accept `open.spotify.com/playlist/...` and `music.apple.com/.../playlist/...` links; fetch the public embed/web page in Rust (no OAuth tokens, 10 s timeout, honest error if private/unavailable) and extract `(playlistTitle, [(title, artist, durationSecs?)])` from Spotify's embed `__NEXT_DATA__` JSON / Apple Music's `application/ld+json` `MusicPlaylist` schema.
  (2) **Matching & persistence**: single-flight cancelable Rust command `library.playlist.import` resolving each track via InnerTube search (`"$title $artist"`, scoring title/artist similarity + duration proximity, 200 ms rate limit, skipping when offline), creating the sled playlist, and returning `{ playlistId, playlistName, total, matched, unmatched: [{title, artist}] }`.
  (3) **UX**: "Import playlist" button in Onboarding (bottom of step 1 & step 2) and Settings → Library group, live `"Matching X of M…"` progress sheet, and graceful completion dialog `"Imported “<name>” — matched N of M tracks"` with an expandable list of unmatched entries.
  Aryan — OK to implement both URL + file import with this flow, or prefer file-only / URL-only for v1?

### 2026-11-21 16:38 UTC — agent-prism
- STATUS: CLAIMED
- TASK: T17
- FILES: android/shared/src/commonMain/.../{library/LibraryScreen,home/HomeScreen,detail/DetailScreen,player/NowPlayingScreen}.kt, android/shared/src/desktopTest/.../SharedCoreTests.kt
- WHAT: Claiming task T17: fix copy pluralization for Smart Mixes ("1 track" / "6 tracks"), Downloads pinned card ("30 songs · 307 MB"), and track/song counters across screens, with unit tests.

### 2026-11-21 16:43 UTC — agent-prism
- STATUS: DONE
- TASK: T17
- FILES: android/shared/src/commonMain/.../{library/PlaylistSheets,library/LibraryScreen,home/HomeScreen,detail/DetailScreen,MusicoApp,MusicViewModel}.kt
- WHAT: Added shared pluralization formatters (`formatTrackCount`, `formatSongCount`, `formatDownloadsSummary`) in `PlaylistSheets.kt`; updated Smart Mixes cards on both Library & Home to `"1 track"` / `"6 tracks"`, Downloads pinned card to `"30 songs · 307 MB"` (`"1 song · …"`), Quick Picks & Desktop rail playlists, DetailScreen header (`1 SONG` / `N SONGS`), and queue/offline save toasts. Verify: `./gradlew :shared:desktopTest` + inspect Library Downloads card and Smart Mixes on Home & Library.

### 2026-11-21 16:44 UTC — agent-prism
- STATUS: CLAIMED
- TASK: T18
- FILES: android/shared/src/commonMain/.../{MusicoApp,theme/Motion,components/Common,library/PlaylistSheets,home/HomeScreen,onboarding/OnboardingScreen,player/NowPlayingScreen,player/Backdrops,player/LyricsPane}.kt
- WHAT: Claiming task T18: fix `"Corner size in Px can't be negative (topStart = -0.06…)"` crash (`MusicoApp` underdamped `shellRadius` spring overshooting below `0.dp`) and clamp every dynamic corner, animated dimension, scale, radialGradient radius, and zero-size Canvas across Android & Desktop.

### 2026-11-21 16:51 UTC — agent-prism
- STATUS: DONE
- TASK: T18
- FILES: android/shared/src/commonMain/.../{MusicoApp,theme/Motion,components/Common,library/PlaylistSheets,home/HomeScreen,onboarding/OnboardingScreen,player/NowPlayingScreen,player/Backdrops,player/LyricsPane}.kt
- WHAT: Fixed the root cause of `"Corner size in Px can't be negative"` (`MusicoApp` `shellRadius` underdamped spring overshooting below `0.dp` on collapse) by clamping `RoundedCornerShape(shellRadius.coerceAtLeast(0.dp))`; audited and clamped all dynamic shapes, spring dimensions, scales, alphas, radialGradient radii, and zero-size Canvas guards across `MusicoApp` (`PillItem` `labelWidth`/`iconScale`, `shellScale`), `Motion.kt` (`pressScale`, `breathing`, `shimmer`, `staggerIn`, plus `safeCornerDp`/`safePositiveScale`), `Common.kt` (`AmbientTopWash`, `Artwork`, `EqualizerBars`, `LikeButton`, `MiniPlayFab`), `PlaylistSheets.kt` (`PlaylistMosaicArtwork`), `HomeScreen.kt` & `OnboardingScreen.kt` & `NowPlayingScreen.kt` (pager dots, `LivingBreathingArtwork`, `BigPlayButton`, `repeatScale`), `Backdrops.kt` (`FluidMesh`, `Rain`, `Aurora`), and `LyricsPane.kt`. Verify: `./gradlew :shared:desktopTest` + rapid tapping/resizing/tab switching across every screen on Desktop & Android.

### 2026-11-21 16:52 UTC — agent-prism
- STATUS: CLAIMED
- TASK: T19
- FILES: android/desktopApp/.../Main.kt, android/shared/src/desktopMain/.../core/MusicoCore.desktop.kt, android/shared/src/commonMain/.../ui/MusicoApp.kt
- WHAT: Claiming task T19: desktop general sweep — move playback/navigation shortcuts to bubble `onKeyEvent` so text fields are never hijacked (while keeping `Ctrl+K`/`Ctrl+F`), allow smooth resize below 900 px (`520×560` min) into compact layout, harden packaged native-lib discovery + core-warning banner gating when `MusicoCore.isLoaded == true`.

### 2026-11-21 16:49 UTC — agent-prism
- STATUS: DONE
- TASK: T19
- FILES: android/desktopApp/.../Main.kt, android/shared/src/desktopMain/.../core/MusicoCore.desktop.kt, android/shared/src/commonMain/.../ui/MusicoApp.kt
- WHAT: Kept only `Ctrl+K`/`Ctrl+F` in `onPreviewKeyEvent` and moved `Escape`, plain/`Ctrl+Spacebar`, `Ctrl+1..4`, `Ctrl+L`, and `Ctrl+Arrows` to bubble-phase `onKeyEvent` (reading `vm.state.value`) so focused text fields never get hijacked; lowered `window.minimumSize` to `520×560` so resizing below 900 px transitions cleanly across the `840.dp` compact breakpoint; sized `DesktopDockedPlayerBar` right controls (`36.dp` icon buttons + responsive `widthIn(56.dp, 104.dp)` volume slider) so 840–900 dp never clips; clamped `shellDim.coerceIn(0f, 1f)`; cleared `_loadError` on all successful loads and added packaged bundle search paths (`compose.application.resources.dir`, `app.dir`, jar dir) in `MusicoCore.desktop.kt`. Verify: Desktop smoke test (resize across 840 px, edit text in Search/Rename, shortcuts, packaged bundle banner check).

### 2026-11-21 16:50 UTC — agent-prism
- STATUS: DONE
- TASK: Round-3 sweep + unit tests
- FILES: android/shared/src/commonMain/.../{onboarding/OnboardingScreen,ui/MusicViewModel}.kt, android/shared/src/desktopMain/.../platform/DesktopPlayback.kt, android/shared/src/desktopTest/.../SharedCoreTests.kt
- WHAT: Fixed 3 additional bugs found during sweep: (1) `OnboardingScreen` artist search `LazyColumn` keyed by raw `it.videoId` (crash risk on duplicate/blank IDs) → `itemsIndexed` with index-disambiguated keys; (2) `MusicViewModel.search` lacked a cancellable `searchJob`, and `searchArtists`/`onQueryChange`/`search` could let stale/cancelled coroutines overwrite state or flip loading spinners via `runCatching` swallowing `CancellationException`; (3) `DesktopPlayback.playFile` left `_isBuffering = true` on native error (`try/finally` fix) and `release()` cancelled `scope` immediately after launching `player.stop` (now stops engine cleanly with a 300 ms timeout before `scope.cancel()`). Added unit tests in `SharedCoreTests.kt` for T14 (`snapCacheCapMb`), T15 (`mapFiveBandsToTenDb` + Desktop EQ prefs persistence), T17 (count/downloads formatters), and T18 (`safeCornerDp`/`safePositiveScale`). Static checks passed across all 26 Kotlin + 7 Rust + 2 XML modified files (no `cargo`/`gradle` in container). Verify: `cargo test --workspace` and `./gradlew :shared:desktopTest`.

### 2026-11-22 16:22 UTC — agent-solstice
- STATUS: CLAIMED
- TASK: T20
- FILES: android/shared/src/commonMain/.../{components/Common,player/Backdrops,player/NowPlayingScreen,player/LyricsPane,ui/MusicoApp,home/HomeScreen,detail/DetailScreen,library/LibraryScreen,library/PlaylistDetailScreen,radio/RadioScreen,profile/ProfileScreen,library/PlaylistSheets}.kt
- WHAT: I'm agent-solstice, claiming task T20: eliminate every hard horizontal gradient cutoff via reusable `Modifier.musicoWash(...)` (offscreen cubic alpha feathering to 0.0 before geometric bounds), audit all `Brush.*Gradient` scrims/washes, overhaul Aurora (volumetric boreal ray curtains + arctic starfield) and Rain (cinematic rain-on-clear-dark-glass droplets, rivulets & soft bokeh), and restyle Desktop's bottom dock into a floating M3 Expressive squircle pill.

### 2026-11-22 17:08 UTC — agent-solstice
- STATUS: DONE
- TASK: T20
- FILES: android/shared/src/commonMain/.../{components/Common,player/Backdrops,player/NowPlayingScreen,player/LyricsPane,home/HomeScreen,library/LibraryScreen,detail/DetailScreen,ui/MusicoApp}.kt
- WHAT: Added reusable `Modifier.musicoWash(...)` (offscreen compositing + height-coupled Gaussian radial blooms + multiplicative `BlendMode.DstIn` cubic fade to 0.0 at 98% height so no wash can ever create a horizontal cutoff) and `musicoScrimBrush(...)`; refactored `AmbientTopWash` onto `musicoWash`; replaced abrupt 3-stop scrims in `HomeScreen`, `LibraryScreen`, and `DetailScreen`; upgraded `NowPlayingScreen` artwork halo to bounded 5-stop Gaussian decay and `LyricsPane` vertical fade mask to an 8-stop cubic feather; overhauled `Backdrops.kt` (volumetric 3-curtain feathered vertical-ray Aurora with arctic starfield, realistic rain-on-clear-dark-glass Rain with drifting bokeh, depth-layered streaks, 3D refracting beads & sliding rivulets, plus smooth 6-stop readability scrims on Fluid/ArtworkGlow/Midnight); converted Desktop's bottom player bar in `MusicoApp.kt` into a floating 76 dp M3 Expressive squircle pill dock (`28.dp` radius, subtle glow border, smooth 4-stop horizontal cover tint) and smoothed `MiniPlayer`'s horizontal gradient. Verify: inspect all screens in all 5 palettes and all 5 backdrops at multiple Desktop window sizes and on Android — zero gradient cutoffs.

### 2026-11-22 17:08 UTC — agent-solstice
- STATUS: CLAIMED
- TASK: T21
- FILES: android/shared/src/commonMain/.../{theme/Theme,theme/Motion,components/Common,ui/MusicoApp,ui/MusicViewModel,home/HomeScreen,search/SearchScreen,radio/RadioScreen,library/LibraryScreen,library/PlaylistDetailScreen,detail/DetailScreen,player/NowPlayingScreen,player/LyricsPane,settings/SettingsScreen,settings/EqScreen,onboarding/OnboardingScreen,profile/ProfileScreen}.kt
- WHAT: Claiming task T21: uniform Material 3 Expressive polish audit across all 12 screens — complete 5-rung palette-tinted tonal surface ladder & 4dp spacing/type scale in `Theme.kt`, matching-geometry shimmer skeletons for Search/Radio/Detail, inline retry on `DetailScreen`, eliminate ad-hoc fontSizes and cards-inside-cards, unify washes and icon discipline.

### 2026-11-22 17:32 UTC — agent-solstice
- STATUS: DONE
- TASK: T21
- FILES: android/shared/src/commonMain/.../{theme/Theme,components/Common,ui/MusicViewModel,ui/MusicoApp,home/HomeScreen,search/SearchScreen,radio/RadioScreen,detail/DetailScreen,settings/SettingsScreen,settings/EqScreen,profile/ProfileScreen,onboarding/OnboardingScreen}.kt
- WHAT: Completed 12-screen M3 Expressive polish audit:
  - `Theme.kt`: completed 5-rung palette-tinted tonal ladder (`surfaceContainerLowest`..`surfaceContainerHighest`), added 4dp `MusicoSpacing`, `MusicoShapes.tile`/`pillDock`, and unified `headline`/`titleLarge`/`overline` tokens with explicit line-heights.
  - Per-screen checklist: (1) Home: `HeroCard` unified to `overline`/`headline`, seamless wash & scrims; (2) Search: added `AmbientTopWash`, `TrackListSkeleton` replacing spinner, cleaned empty copy; (3) Radio: adaptive shimmer tile grid replacing spinner, `MusicoShapes.tile` & `headline`/`titleLarge`/`overline`; (4) Library: audited — clean wash, scrims, copy, tonal cards; (5) PlaylistDetail: audited — clean wash, actions, pluralization; (6) Detail: added `DetailPageSkeleton`, wired `vm.retryTopDetail()` inline retry, unified `overline`/`headline`; (7) Player & (8) Lyrics: audited — Gaussian halo, 8-stop feather, unified motion; (9) Settings: added top `musicoWash`, flattened `SettingIcon` cards-inside-cards into tinted `Box`, clean About copy & tokens; (10) EQ: added top `musicoWash`, unified `overline`/`caption`/`tabularCaption`; (11) Onboarding: full-bleed top `musicoWash` behind `musicoTopInset`; (12) Profile + Desktop Rail: top `musicoWash`, palette-driven avatar gradient, flat `LinkRow` icon tiles, clean rail version subtitle.

### 2026-11-22 17:32 UTC — agent-solstice
- STATUS: CLAIMED
- TASK: T22
- FILES: android/shared/src/commonMain/.../{ui/MusicViewModel,home/HomeScreen,ui/MusicoApp}.kt, android/shared/src/androidMain/.../player/PlaybackController.kt, android/shared/src/desktopMain/.../platform/DesktopPlatform.kt, android/desktopApp/build.gradle.kts, src-tauri/Cargo.toml, src-tauri/tauri.conf.json, package.json, src-tauri/src/mobile.rs, src-tauri/src/online/{stream_server,stream,sources,saavn}.rs
- WHAT: Claiming task T22: (1) fix persistent "Couldn't build shelves for: English" banner with language alias mapping + search fallback + retry/dismiss; (2) sync reported version across About/`app.update.check`/Desktop/manifests to `0.9.6-cmp` (`0.9.6` strict semver); (3) randomize `stream_server.rs` session tokens using 32-hex CSPRNG (`OsRng`); (4) fix Desktop shell resize/morph double-clip & `movableContentOf` state preservation across `840.dp`; (5) slash cold-start playback latency (~10s → fast) via async core prewarm, parallel JioSaavn vs InnerTube race, 32 KiB initial buffer gate, and Android `MediaController` pre-bind.

### 2026-11-22 18:03 UTC — agent-solstice
- STATUS: DONE
- TASK: T22
- FILES: android/shared/src/commonMain/.../{ui/MusicViewModel,home/HomeScreen,ui/MusicoApp}.kt, android/shared/src/androidMain/.../player/PlaybackController.kt, android/shared/src/desktopMain/.../platform/DesktopPlatform.kt, android/shared/src/desktopTest/.../SharedCoreTests.kt, android/desktopApp/build.gradle.kts, src-tauri/Cargo.toml, src-tauri/tauri.conf.json, package.json, src-tauri/src/mobile.rs, src-tauri/src/online/{stream_server,stream,sources,saavn}.rs
- WHAT: Completed all 5 correctness & latency fixes: (1) Added `resolveLanguageGenreStyle` (mapping regional language labels like `"English"` → Pop/Indie/Rock/etc.) + `"$lang top hits"` search fallback in `personalizeHome`, gated the Home error banner to only show when no shelves could be built, and upgraded `PersonalizationBanner` with Retry & Dismiss actions (+ Kotlin test); (2) Synced reported version across `Cargo.toml`, `tauri.conf.json`, `package.json`, `desktopApp/build.gradle.kts`, `DesktopPlatform.kt`, and `mobile.rs` (`app_display_version()` → `"0.9.6-cmp"`) with Rust unit test coverage; (3) Replaced predictable `s<counter>_<stem>` tokens in `stream_server.rs` with 32-hex CSPRNG tokens from `rand::rngs::OsRng` (loopback-only kept + hermetic test `stream_tokens_are_random_and_unpredictable`); (4) Folded rounded clip directly into `MusicoApp`'s single shell `graphicsLayer` and hoisted `MainTabContent` via `movableContentOf` to eliminate Desktop resize/morph double-draw; (5) Slashed cold-start playback latency by prewarming `stream_server`, `sources` TLS/visitor cache, and `saavn` client off-thread in `init_core`, racing JioSaavn vs InnerTube in parallel (`mpsc::channel`, 2.5 s cross-source cap), lowering `INITIAL_READY_BYTES` to 32 KiB, and pre-binding Android `MediaController` in `PlaybackController.init`. Verify: `cargo test --workspace`, `./gradlew :shared:desktopTest`, and device/Desktop smoke checks.

### 2026-11-22 18:03 UTC — agent-solstice
- STATUS: CLAIMED
- TASK: T23
- FILES: src-tauri/src/online/{playlist_import,mod}.rs, src-tauri/src/mobile.rs, android/shared/src/commonMain/.../{core/Models,platform/Platform,ui/MusicViewModel,ui/MusicoApp,ui/library/PlaylistSheets,ui/onboarding/OnboardingScreen,ui/settings/SettingsScreen}.kt, android/shared/src/androidMain/.../platform/AndroidPlatform.kt, android/shared/src/desktopMain/.../platform/DesktopPlatform.kt, android/shared/src/desktopTest/.../SharedCoreTests.kt
- WHAT: Claiming task T23: implement minimal v1 Playlist Import (both file import via Android SAF `OpenDocument` / Desktop `FileDialog` for Exportify/TuneMyMusic/Soundiiz CSV, Spotify/Music0 JSON, M3U/M3U8, and public URL import for `open.spotify.com/playlist/...` & `music.apple.com/.../playlist/...` with SSRF allowlist + 10 s timeout), single-flight cancelable Rust command `library.playlist.import` (+ `.status`/`.cancel`) with InnerTube matching & sled persistence, and clean M3 Expressive UI wired into Onboarding & Settings → Library.

### 2026-11-22 18:41 UTC — agent-solstice
- STATUS: DONE
- TASK: T23
- FILES: src-tauri/src/online/{playlist_import,mod}.rs, src-tauri/src/mobile.rs, android/shared/src/commonMain/.../{core/Models,platform/Platform,ui/MusicViewModel,ui/MusicoApp,ui/library/PlaylistSheets,ui/onboarding/OnboardingScreen,ui/settings/SettingsScreen}.kt, android/shared/src/androidMain/.../platform/AndroidPlatform.kt, android/shared/src/desktopMain/.../platform/DesktopPlatform.kt, android/shared/src/desktopTest/.../SharedCoreTests.kt
- WHAT: Implemented minimal v1 Playlist Import across Rust, Android, and Desktop: (1) new `playlist_import.rs` with RFC-4180 CSV (Exportify/TuneMyMusic/Soundiiz), Spotify/Music0 JSON, and M3U/M3U8 parsers (4 MiB / 500-track caps), strict HTTPS + exact host allowlist (`open.spotify.com`, `music.apple.com`) + redirect-guarded public URL fetcher (10 s timeout) extracting Spotify embed `__NEXT_DATA__` and Apple Music `application/ld+json`, plus normalized title/artist/duration match scorer and 6 hermetic Rust tests; (2) single-flight cancelable `library.playlist.import`, `.status`, and `.cancel` commands in `mobile.rs` (200 ms InnerTube pacing, sled persistence, RAII guard, dispatch validation test); (3) cross-platform `rememberPlaylistFilePicker` (`OpenDocument` on Android SAF, `FileDialog` on Desktop), `MusicViewModel` polling & cancellation, `ImportPlaylistDialog` (live `"Matching X of M…"` progress, cancel, completion summary, expandable unmatched list, Open playlist), wired into Onboarding & Settings → Library (+ Kotlin DTO test). Verify: `cargo test -p musico-online playlist_import`, `./gradlew :shared:desktopTest`, and interactive URL/file imports on Android & Desktop.

### 2026-11-22 18:41 UTC — agent-solstice
- STATUS: CLAIMED
- TASK: T24
- FILES: CHANGELOG.md, README.md
- WHAT: Claiming task T24: update `CHANGELOG.md` with the `0.9.6` (Unreleased) entry covering the Compose Multiplatform architecture, Round 3 stability/streaming/EQ/downloads, and Round 4 polish/import/latency upgrades, and update `README.md` with the Desktop Compose Multiplatform section (CI bundle + `MUSICO_NATIVE_LIB`) without tagging or cutting a release.

### 2026-11-22 18:45 UTC — agent-solstice
- STATUS: DONE
- TASK: T24
- FILES: CHANGELOG.md, README.md
- WHAT: Added `## [0.9.6] - Unreleased` section to `CHANGELOG.md` covering the CMP Android + Desktop restructure, stream-while-downloading playback, Playlist Import (URL + CSV/JSON/M3U), Downloads enrichment, EQ parity, seamless `musicoWash` gradients & Aurora/Rain backdrop overhauls, M3 Expressive polish audit, fast cold-start playback, and Round 3/4 fixes; updated `README.md` status to `0.9.6-cmp` (in development), added a **Desktop Compose App (Compose Multiplatform)** section documenting CI bundles and `MUSICO_NATIVE_LIB`, and added `:shared:desktopTest` and `android/` to Testing & Project layout. Versions kept at `0.9.6-cmp` (`0.9.6` strict semver) with no release/tag cut. Verify: review `CHANGELOG.md` and `README.md`.

### 2026-11-22 21:35 UTC — Aryan (logged via Bella, review assistant)
- STATUS: DECISION LOG (retroactive, recorded so the bus has them)
- TASK: —
- WHAT: Product decisions attributed to their owner, Aryan: (1) Round-2 palette naming (Nebula/Ocean/Ember/Forest/Mono) was Aryan's spec — the "Palette.NEBULA case mismatch" fix corrected an agent test reference, not the design; (2) Tauri retirement + v1.0.0 direction is Aryan's explicit decision (2026-11-21), executed in PR #132 `fix/retire-tauri-compose-v1` stacked on #131; (3) T16 playlist import: BOTH URL + file paths, v1-simple (implemented in #131 per this approval); (4) Release policy unchanged: v0.9.5 stays Latest — no v1.0.0 tag until Aryan says so. Process note going forward: ALL product decisions (Aryan's included) must be logged here when they are made, not retroactively.

### 2026-11-22 21:55 UTC — Aryan (logged via Bella, review assistant)
- STATUS: TASKS POSTED — round 5 (much-needed + polish)
- TASK: T25 (MUCH NEEDED — instant-start playback) + T26 (backdrop v2)
- FILES: src-tauri/src/online/{stream.rs,stream_server.rs,sources.rs}, musico_core paths after rename, android/shared/.../DesktopPlayback.kt, PlaybackController.kt, player/Backdrops.kt
- WHAT: Aryan's report + directives, verbatim intent: **T25 — "songs are taking like 10 secs to load from cold start; I think it's resolving the whole track first then starting to play. It should start playing the second the first 5 secs are available — buffer in between if the user has slow internet, but the start should be instant, just like other big music companies."** T11's loopback pump (64 KiB startup gate) should already give instant start — so MEASURE where the ~10 s actually goes before coding: (a) does the DESKTOP `DesktopPlayback.playFile` path use `resolve_track_streaming`/loopback URL at all, or does it still block on whole-file `track.resolve` → play local file? (b) resolve-chain latency: Piped/Invidious serial failover can burn seconds before the pump starts — parallelize/first-success, cache last-good instance, and pre-resolve the NEXT track while the current one plays. Target: click-to-audio < 2.5 s cold, < 1 s warm, on both platforms; instrument timings (log click→resolved→first-byte→audio) and add a hermetic test for the startup-gate behavior. **T26 — "the Fluid water effect looks AI-generated; we want a clean rain-on-clear-glass look"**: replace/upgrade the Fluid backdrop with a rain-on-glass effect. Evaluate implementation honestly and pick with a bundle-size budget: bundled seamless MP4 loop (Android: Media3 video composable is trivial; Desktop: no first-party video composable in CMP — evaluate a community player lib vs a Skia/AGSL-style rain shader port vs divergent implementations) — report findings in the Log BEFORE implementing, then implement behind the existing backdrop picker (Fluid → Rain on glass, other backdrops unchanged).

### 2026-11-22 22:1x UTC — Aryan (logged via Bella, review assistant)
- STATUS: TASK POSTED — round 5 (real-world feedback from v1.0.0 users)
- TASK: T27 — updater UX vs Google Play Protect
- FILES: android/shared/src/commonMain/.../MusicViewModel.kt (update flow), android/shared/src/androidMain/.../updater/UpdateManager.kt, README.md
- WHAT: Google Play Protect shows "Harmful app blocked — This app may be harmful" when the v1.0.0 update APK installs via the in-app updater (and on first installs). Verified NOT malicious: the 1.0.0 APK's signing cert is byte-identical to the 0.9.5-era keystore (SHA-256 0cd961a9…f12353). Play Protect heuristics flag brand-new APK hashes + self-updating apps (the classic "unknown dropper" pattern — same dialog every sideloaded FOSS app gets, e.g. NewPipe). Fix plan: (1) short-term — users tap "Install anyway"; document this in README (NewPipe-style) and in the release notes; (2) updater UX — before triggering the package installer, show a one-time info sheet: "Your phone may warn about this install — tap Install anyway. Music0 is signed with the same key since 0.9.5 and every release ships SHA256SUMS"; (3) submit the v1.0.0 APK to Google's Play Protect false-positive appeal so the verdict clears over time (installs accumulate reputation); (4) NOT recommended: Play Store distribution — an InnerTube-streaming app risks policy action on the whole developer account; Aryan's call someday, eyes open.
