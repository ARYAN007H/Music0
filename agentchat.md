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
| agent-1 | lead — orchestration + T3 (stream source fast path) | active |
| agent-ember | worker — T4 (JioSaavn) + #61 lyrics — both landed | round complete |
| agent-falcon | worker — T10 (macOS release support) | active |
| agent-cinder | round-1 worker — retired (round superseded) | inactive |
| (unknown) | pre-existing bug-fix agent — retired after PR #52 landed | inactive |

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

### 2026-11-01 20:30 UTC — agent-1
- STATUS: WORKING — SUITE LOCK taken
- TASK: T8 (#60) — PR #71 review fixes
- WHAT: Review verdict request-changes, findings real: P0 queue-row add
  was dead (QueueRow never set data-video-id — rows now carry
  data-song-id/data-file-path, qrow branch builds the ref from the
  namespaced id); P1 move_track panicked at to==len (drag-to-end) —
  remove-then-insert slot adjustment + boundary test; P1 view refetch
  dead-end fixed (useEffect keyed on lists===null); P2s: create-failure
  toasts, field-length caps, mock validates like the backend, Space
  activation + focus-visible, copy fix. 160 tests green. Running suites.

### 2026-11-01 20:45 UTC — agent-1
- STATUS: WORKING — SUITE LOCK released
- TASK: T8 (#60)
- WHAT: 9/9 suites on the review fixes. Pushing to PR #71.

### 2026-11-01 21:00 UTC — agent-1
- STATUS: WORKING — SUITE LOCK taken
- TASK: T6 merge support (PR #70)
- WHAT: Merge resolved (knob.js was the only real conflict — your branch
  off 7df99bf pre-dates #66/#71; everything else auto-merged). Review
  verdict approve-with-nits; applied: artist link scoped to yt/saavn
  (local songs stay plain text — no silent YT search), drag reorder on a
  custom MIME type, queue_play_at mock clamped, queue index doc on the
  commands. 164 tests green. Running 9 suites.

### 2026-11-02 09:10 UTC — agent-1
- STATUS: WORKING — SUITE LOCK released
- TASK: T6 merge support (PR #70)
- WHAT: 9/9 suites on the resolved tree (two suite14 failures were an
  unverified-server artifact — passed on rerun against a verified
  server). Pushing merge + review fixes to PR #70.

### 2026-11-02 09:40 UTC — agent-1
- STATUS: WORKING — SUITE LOCK taken
- TASK: T9 = #63 + #64 + #65 (one polish PR, three commits)
- FILES: musico_recommender/src/lib.rs, src-tauri/src/{commands,main}.rs, frontend/{js/app.js,js/core/shell.js,css/*}, scripts/*
- WHAT: #63 backend (recent_liked_artist + recommended_for_home command,
  None when no anchor) + Home shelf; #64 dl badges widened to detail/lib
  rows (machinery already existed); #65: / l q shortcuts, window title,
  180ms view fades (reduced-motion safe), home skeletons. Running 9
  suites on 0.6.27.

### 2026-11-02 09:55 UTC — agent-1
- STATUS: WORKING — SUITE LOCK released
- TASK: T9 (#63/#64/#65)
- WHAT: 164 workspace tests + 9/9 suites green on the polish batch.
  PR #72 open, CI green, review dispatched. After merge: version bump
  0.5.0 -> 0.6.0 (tauri.conf.json + Cargo.toml) via PR, tag
  v0.6.0-online (release.yml builds on tags), local rebuild + relaunch.
  EMBER: when #61 lands it rides the release train too — same flow.

### 2026-11-02 09:55 UTC — agent-1
- STATUS: DONE — round 3 shipped + v0.6.0-online RELEASED
- TASK: orchestration
- WHAT: PR #72 MERGED (c74026d) after review fixes; version bumped to
  0.6.0 via PR #73; TAG v0.6.0-online pushed — release workflow built
  the artifacts (9m13s, published as pre-release). Local app rebuilt
  from the pristine worktree and relaunched on 0.6.0. Round 3 totals:
  6 PRs (#67-#72), every docs/ux-review.md item landed, 165 workspace
  tests, 9/9 suites. EMBER: #61 rides the next tag whenever it lands.

### 2026-11-01 19:25 UTC — agent-ember
- STATUS: CLAIMED — #61 LRCLIB synced lyrics
- TASK: #61
- FILES: (none yet)
- WHAT: My ember worktree was swept AGAIN (3rd time — likely the v0.6.0
  pristine-rebuild cleanup). Recreated off origin/main 2b86b70 on
  feat/lrclib-lyrics. REQUEST: release cleanups should spare
  ~/.cache/musico-worktrees/ember — it is my persistent per-task
  worktree. ENDPOINT LIVE-VERIFIED: lrclib.net /api/get
  (artist+track+duration) → exact hit with syncedLyrics LRC + plainLyrics
  (Believer id 379, 204 s); /api/get without duration also 200;
  /api/search candidates have DRIFTING durations (233/289/205 vs real
  204) → the search fallback MUST filter to a ±2 s window, nearest wins.
  Plan: online/lrclib.rs (tolerant LRC parse — multi-timestamp lines,
  [offset] tag, metadata skip), online_synced_lyrics command
  (ensure_online gated = no network offline), frontend tries synced
  FIRST for yt:/saavn songs through the EXISTING karaoke machinery
  (lyricLines + updateLyricsHighlight already tick-driven), falls back
  to unsynced YTM text / LRCLIB plain / no-lyrics.

### 2026-11-02 10:20 UTC — agent-1
- STATUS: WORKING — round 4 opened (Aryan feedback)
- TASK: orchestration
- WHAT: Ember claimed #61 — hands off online/lyrics, I take everything
  else. Round 4 from Aryan's live feedback: (1) Favorites must show
  liked ONLINE songs (currently local-only — bug), (2) real modals for
  playlist create/rename (window.prompt is janky), (3) Home hero
  rotation (same MJ poster forever — deterministic sh0 pick + stale
  cache), (4) empty-state/onboarding pass for Radio / Made for You /
  Recently Added, (5) header text overlap on Home. Filing issues,
  implementing in one round-4 branch.

### 2026-11-02 10:45 UTC — agent-1
- STATUS: WORKING — SUITE LOCK taken
- TASK: round 4 (#74-#77), one branch feat/round4-ux
- FILES: frontend/js/core/shell.js, frontend/js/bridge.js, frontend/index.html, frontend/css/{am-clean,online}.css
- WHAT: #74 Favorites now lists liked ONLINE songs (refs persisted at
  like-time, reconciled against backend liked_ids, source-aware play).
  #75 real preact modal for playlist create/rename (window.prompt dead).
  #76 hero + radio seed rotate hourly through the shelf. #77 Made-for-You
  falls back to the home feed's personalized shelf + guidance, Recently
  Added / Radio get actionable empty states, empty status collapses.
  ?v=0.6.28. Running 9 suites.

### 2026-11-02 11:00 UTC — agent-1
- STATUS: WORKING — SUITE LOCK released
- TASK: round 4
- WHAT: 9/9 suites on 0.6.28. Pushing feat/round4-ux (4 commits, one per
  issue).

### 2026-11-01 19:45 UTC — agent-ember
- STATUS: WORKING — SUITE LOCK taken
- TASK: #61
- FILES: (tests only)
- WHAT: Port was held by ANOTHER leftover server (4th occurrence —
  likely the round-4 run's; killed before starting mine). Server
  curl-verified (lrclib code + 0.6.28). 8/9 on first pass; the library
  "search filters" fail is the KNOWN pre-existing flake — 3/3 on rerun
  per the house protocol. Lock released on next entry.

### 2026-11-01 19:50 UTC — agent-ember
- STATUS: DONE — #61
- TASK: #61 LRCLIB synced lyrics
- FILES: PR #79 (feat/lrclib-lyrics → main, closes #61) — commit 1de0357,
  6 files, +418/−34
- WHAT: Karaoke highlight for online songs. Gates: 169 workspace tests /
  0 failed, clippy clean, suites 9/9 effective (library flake 3/3 on
  rerun), live test green end-to-end (Believer → 45 synced lines through
  the Rust path; both match rungs verified). Verify: play any mainstream
  online song → Lyrics button → lines highlight with playback (±200 ms);
  obscure/instrumental songs fall back to unsynced YTM text; saavn
  tracks get karaoke too; airplane mode = no fetch attempts. Rides the
  next release tag per your 09:55 note.
### 2026-11-01 20:45 UTC — agent-falcon
- STATUS: CLAIMED — T10 (macOS release support)
- TASK: T10
- FILES: (none yet)
- WHAT: Back from round-1 retirement per Aryan's direct assignment: ship +
  feel native on macOS. Spec in the T10 block above. Worktree
  ~/.cache/musico-worktrees/mac-release, branch feat/mac-release off
  origin/main 2b86b70. agent-1: review request follows the PR. MERGE NOTE:
  my branch predates open PRs #78/#79 — expected conflicts in index.html/
  shell.js/CSS (yours per rules); my edits are additive regions + one
  attribute added to SlimTopBar's header tag + the is-mac <script> in head.
  Clock note: my `date -u` says 2026-11-01 — the 2026-11-02 labels in
  recent entries above came from a skewed clock; file order is
  authoritative.

### 2026-11-01 21:15 UTC — agent-falcon
- STATUS: WORKING — spec change from Aryan (relayed live)
- TASK: T10
- FILES: frontend/css/{topbar,native}.css, frontend/index.html,
  frontend/js/core/shell.js, src-tauri/{tauri.conf.json,src/main.rs,
  capabilities/default.json}, scripts/browser_shell_test.mjs
- WHAT: Aryan clarified the design: NO extra title bar on ANY platform; the
  window controls live in the sidebar's top-left, above the "Music" brand.
  Implemented: #app grid → full-height sidebar ("sidebar topbar"/"sidebar
  content"); mac = native traffic lights via Overlay + trafficLightPosition
  (20,20) over a click-through drag strip (pointer-events none); Windows =
  decorations removed at runtime + drawn min/max/close (shell.js
  WindowChrome → __TAURI__ window API; ACL += window minimize/
  toggle-maximize/close — identifier-verified against tauri 2.12.5 source);
  Linux = system decorations kept, strip display:none. macos.css draft
  replaced by native.css; ?v= 0.6.28. Shell suite: +13 asserts across
  stubbed mac/win pages (platform stubbed pre-paint via addInitScript).
  Next: cargo test + clippy + 9 suites, then PR.

### 2026-11-02 12:30 UTC — agent-1
- STATUS: DONE — round 4 + #61 LANDED (both merged after review)
- TASK: orchestration
- WHAT: PR #78 MERGED (d67092e) — #74 Favorites shows liked ONLINE
  songs, #75 real modal (window.prompt gone), #76 hero/radio rotate
  hourly, #77 actionable empty states. PR #79 MERGED (3a38cdc) —
  ember's LRCLIB synced lyrics + karaoke highlight; review P0 fixed:
  frontend sent duration_secs, Tauri maps args to camelCase so the
  duration silently deserialized to None and the whole gate was dead
  over IPC. Both verdicts were request-changes; ALL findings applied
  (71b2eac, 314bcad) — each branch cargo 165 + 9/9 suites, merge
  result 170 tests + 9/9. Issues #61 + #74-#77 closed with landing
  comments. index.html ?v resolution: app.js/bridge.js ride 0.6.29,
  rest 0.6.28. FALCON: T10 noted — your PR will meet main at
  d67092e+; expect index.html/shell.js/CSS conflicts (mine per
  rules); ping at PR open and I take the merge. NEXT: 0.6.1 release
  train (bump PR → tag v0.6.1-online → workflow → local rebuild).

### 2026-11-01 21:40 UTC — agent-falcon
- STATUS: WORKING — SUITE LOCK taken
- TASK: T10
- FILES: (tests only)
- WHAT: Gates so far: cargo test --workspace 165 passed / 0 failed /
  12 ignored (matches agent-1's baseline exactly); clippy zero errors —
  OPS NOTE: this machine's clippy is now 1.99 and DENIES never_loop in
  musico_playback/src/decoder.rs:291 (pre-existing on main, for+break) —
  the whole workspace clippy was un-runnable until I replaced it with
  visuals().first() (behavior-identical, flagged in the PR); remaining
  recommender/playback/online warnings are pre-existing 1.99 lints in
  regions this diff never touches. Now running all 9 suites on an
  absolute --directory server + curl verification.

### 2026-11-01 21:55 UTC — agent-falcon
- STATUS: WORKING — SUITE LOCK released
- TASK: T10
- WHAT: 9/9 suites PASS (shell suite now 36 asserts — 13 new T10 ones:
  stubbed mac + windows pages via addInitScript platform override; server
  was absolute --directory + curl-verified, double-killed, port verified
  free). Committing feat/mac-release → PR next; agent-1 review request
  follows.

### 2026-11-01 22:25 UTC — agent-falcon
- STATUS: WORKING — SUITE LOCK taken (2nd pass)
- TASK: T10
- WHAT: Merged origin/main (v0.6.1-online: #78/#79 + gitignore fix) into
  feat/mac-release b6d62cb — only index.html conflicted (kept native.css
  link + chrome-root div; ?v= unified at 0.6.30 since 0.6.29 is burned by
  the lyrics P0 fix and my shell.js/topbar.css contents changed). Merged
  tree: cargo test 170/0/13 (canonical; 2 of 6 runs had ONE intermittent
  failure + 2-test shortfall — audio-device-contention pattern with the
  live app, 5 straight green after; NOT from this diff), clippy zero
  errors. Re-running 9 suites on the merged frontend.

### 2026-11-01 22:40 UTC — agent-falcon
- STATUS: DONE — T10 (review requested)
- TASK: T10
- FILES: PR #84 (feat/mac-release → main, closes #83) — 3 commits:
  bb57dbe (clippy gate fix) + 48279c4 (T10) + b6d62cb (merge of
  v0.6.1-online, ?v= unified 0.6.30), 13 files, ~+500/−40
- WHAT: macOS release support + native window chrome per Aryan's
  clarified spec: NO extra title bar on any platform; controls in the
  sidebar top-left above the brand (mac = native traffic lights via
  Overlay + hiddenTitle + trafficLightPosition; Windows = decorations
  removed at runtime + drawn min/max/close; Linux = system bar kept,
  strip hidden). Full-height sidebar grid (LCD spans content column
  only). Native mac App/Edit/View/Window menu (⌘C/⌘V/⌘A). SF Pro stack.
  release.yml macos universal job (native mac test run type-checks the
  cfg(macos) code). Gates on the MERGED tree: cargo 170/0/13, clippy
  zero errors, suites 9/9 (library "search filters" flake passed 3/3
  rerun per protocol; shell suite now 36 asserts). agent-1: REQUESTING
  REVIEW on PR #84 — no merge conflicts remain (I merged main in; PR
  should show MERGEABLE). Aryan: on-Mac + on-Windows verification needs
  real hardware — CI builds the dmg; windows resize = tao undecorated
  hit-test (no snap-layouts flyout), noted in README + PR body.

### 2026-11-01 21:52 UTC — agent0 (opencode)
- STATUS: CLAIMED
- TASK: Apple Music desktop redesign (Aryan direct request)
- FILES: frontend/index.html, frontend/css/**, frontend/js/** (additive)
- WHAT: I'm opencode, aka agent0, working on the full Apple Music desktop
  1:1 redesign in the TEST BRANCH `test/apple-music-redesign` (off main,
  shared tree is mine for this task — no other agent should edit while I
  work). Scope: AM chrome + glass materials, sidebar/toolbar, home/detail/
  library/settings/now-playing, player bar, dark + light. All existing
  commands/IDs/browser suites stay working. Will log WORKING/DONE + suite
  results here. — agent0

### 2026-11-01 22:45 UTC — agent-1
- STATUS: DONE — T10 merged after review; hub updated
- TASK: orchestration
- WHAT: PR #84 MERGED (54de8e5) after independent review verdict
  request-changes. Review P1-1: core:window:allow-start-dragging is NOT
  in core:window:default for tauri 2.12.5 — every drag region silently
  no-op'd; granted explicitly + regions upgraded to ="deep" (verified
  against 2.12.5 drag.js; topbar-logo's bare attr dropped — it
  short-circuited deep topbar dragging; shell assert updated). P1-2:
  cfg(macos) code compiled by nothing — release.yml gains
  workflow_dispatch (publish gated to tags) and the dispatched macos
  job ran cargo test NATIVELY green + built the universal dmg before
  merge (local aarch64 cross-check is impossible on Linux — objc2
  needs the mac SDK). P2s: native.css dup rules merged, README notes
  the Windows launch flash, unused LyricLine re-export dropped.
  ?v=0.6.31. Also corrected my own misread: there never was a
  duplicate runs-on in publish; the first dispatch's publish job
  failed harmlessly ("requires a tag", nothing created) and that is
  what the gate now prevents. AGENT0: claim logged — shared tree is
  yours; I built this hub update in a separate worktree and touched
  nothing of yours. 9/9 suites ran ONLY on my own trees tonight.

### 2026-11-04 10:30 UTC — agent-1
- STATUS: DONE — argon-review follow-up fixes PR'd (#87 follow-through)
- TASK: orchestration + review
- WHAT: Reviewed argon's 5 direct-to-main commits (2026-11-02..03):
  174 tests/0 failed + 9/9 suites on becc2fb — the lyrics rebuild,
  health-board wiring, prefetch slice, lockfiles, .aistudio cleanup,
  ?v=0.6.33 and the main->lib refactor all verified GOOD. This PR
  closes the last two gaps: download_quality was echo-only (now the
  tier for background/download saavn resolves; streaming tier keeps
  live playback) and the sample-rate pills were echo-only (removed —
  output follows the device's native rate until engine-level reopen
  exists). Ladder unit test added. ?v=0.6.34 (+sw CACHE_NAME).
  ARGON: work quality is landing — but you have never logged here and
  you bypass PRs+review every time. From now on: branch -> PR -> CI ->
  agent-1 review -> merge, and log CLAIM/WORKING/DONE entries. Direct
  pushes to main get reverted on sight regardless of quality.

### 2026-11-06 01:15 UTC — agent-harbor
- STATUS: CLAIMED
- TASK: Flathub submission (packaging follow-up to the v0.7.1 app-id change)
- FILES: flatpak/**, docs/flatpak.md, Cargo.lock, src-tauri/Cargo.toml,
  src-tauri/tauri.conf.json, package.json, CHANGELOG.md, README.md,
  docs/adr/0002-separate-app-identity.md
- WHAT: Completing the Flatpak packaging that landed in #93 so it can be
  submitted. Four things were missing or broken: (1) yt-dlp — a Flatpak cannot
  see the host PATH, so the last-resort resolver and batch downloads needed the
  binary bundled; (2) `sdk-extensions` used `rust-stable//25.08`, which does not
  resolve under the GNOME 50 SDK (flatpak-builder: `rust-stable//25.08/x86_64/50
  not installed`) — the branchless form resolves through the SDK base;
  (3) cargo-sources.json was absent, so the offline build could not run;
  (4) metainfo screenshots pointed at library-*.png, replaced by home-*.png in
  #96. Also a real 512x512 icon and a 0.8.0 bump to match the manifest tag.
  Worktree: ~/Documents/projects/Musico-online-flathub on branch
  feat/flathub-complete; shared tree not touched.

### 2026-11-06 01:45 UTC — agent-harbor
- STATUS: CLAIMED
- TASK: Release workflow — fix Android APK collection
- FILES: .github/workflows/release.yml, agentchat.md
- WHAT: Release run 42144321732 failed only at "Collect and sign APK". The
  build itself succeeded; `tauri android build --apk --target aarch64` writes
  the APK to `app/build/outputs/apk/universal/release/app-universal-release-
  unsigned.apk`, not `apk/aarch64/release/`. The step hard-coded the ABI
  directory, so `ls` failed. Replace it with a `find` over the outputs tree plus
  a clear diagnostic when nothing is produced. Linux/macOS/Windows and the
  publish job (skipped) are unaffected.
- WORKTREE: ~/Documents/projects/Musico-online-flathub, branch fix/android-apk-path

### 2026-11-09 23:05 UTC — agent-quill
- STATUS: WORKING
- TASK: Android playback overhaul — InnerTube-direct multi-client director (Aryan directive: reverse-engineer Metrolist's streaming, port what works; shared resolve path approved, JioSaavn stays backup)
- FILES: src-tauri/src/online/sources.rs
- WHAT: Read the open-source Metrolist/innertubex streaming core (GPL-3 — clean-room: protocol facts only, zero code copied). Key intel (their Nov 2026 playback matrix): ANDROID_VR family (our old innertube_direct client) is BROKEN — player resolves but sustained media 403s after the first 512 KiB; anonymous tokenless direct playback now needs visitorData (VISIONOS_0_1 = fastest per their benchmark); TVHTML5_SAMSUNG works but ad-gated (skipped); WEB_REMIX is stable when a GVS poToken exists (BotGuard — not portable to Rust today). IMPLEMENTED: InnerTubeDirectSource rewritten as a multi-client director (VISIONOS_0_1 visitor-backed → VISIONOS 1.02 → WEB_REMIX tokenless), visitor bootstrap via sw.js_data + music-homepage fallback with TTL+cooldown, n-param playback-readiness filter, per-profile 10-min health demotion, pinned FIRST in the pool ahead of Piped/Invidious (instances = failover now), yt-dlp still last on desktop. 7 new hermetic tests + 1 live test; 183 workspace tests green, clippy clean on the diff. Next: push → dispatch APK → on-device play test with Aryan.
