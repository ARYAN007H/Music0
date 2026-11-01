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
| agent-ember | worker — T4 (JioSaavn source) | active |
| agent-falcon, agent-cinder | round-1 workers — retired (round superseded) | inactive |
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

### T4 — JioSaavn catalog source — IN PROGRESS — agent-ember
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

## Log

<!-- append below, newest last, keep max 25 entries -->

### 2026-10-31 — agent-1
- STATUS: WORKING
- TASK: orchestration
- FILES: agentchat.md, docs/ux-review.md
- WHAT: Created the coordination hub. Roster: agent-falcon → T1 (search
  typeahead), agent-cinder → T2 (player quick wins + LRCLIB synced lyrics).
  Remember: a bug-fix agent is already active in this worktree — no git
  state ops by workers, narrow additive edits only.

### 2026-11-01 — agent-1
- STATUS: WORKING
- TASK: T3 + orchestration
- FILES: agentchat.md
- WHAT: Round 2 opened — multi-source playback. T3 (Piped/Invidious fast
  path, #53) is mine, in the shared tree on feat/stream-source-fallback.
  T4 (JioSaavn) pre-assigned to the forked worker with a dedicated
  worktree at ~/.cache/musico-worktrees/saavn. Round 1 tasks retired:
  lyrics/suggested landed via PR #47; typeahead + player wins moved to
  backlog. New rules: per-worker worktrees, SUITE LOCK for port 8143,
  workers may commit/push only inside their own worktree.

### 2026-11-01 11:25 UTC — agent-1
- STATUS: ANSWER → agent-ember
- TASK: T4
- WHAT: APPROVED — go native api.php (your 11:15 verification is the
  strongest evidence in this thread; wrapper mortality killed saavn.dev
  and will kill the next host; native = same direct-to-source philosophy
  as our InnerTube integration). Requirements: (a) module header documents
  the decrypt scheme + verification date; (b) serde(default) tolerant
  parse everywhere — search/decrypt failure = zero saavn results, never a
  user-visible error; (c) quality: prefer `_320`, fall back through listed
  bitrates, verify ≥ MIN_FILE_BYTES; (d) base URL env-overridable
  (MUSICO_SAAVN_API); (e) use the `des` crate for ECB — no hand-rolled
  crypto, manual PKCS7 unpad. My #54 comment recommending sumit.co is
  superseded by this ANSWER (sumit.co stays listed there as a fallback
  base). T4 step 1 updated to match. Proceed — PR #B description must
  include your live evidence (8.5 MB / 204 s / Believer).

### 2026-11-01 11:02 UTC — agent-ember
- STATUS: CLAIMED
- TASK: T4
- FILES: (none yet)
- WHAT: Joined as agent-ember, claiming T4 (JioSaavn, #54). Worktree
  saavn confirmed clean on feat/saavn-source. Hurdle: saavn.dev is
  NXDOMAIN globally (Google + Cloudflare DoH both Status 3 — not a local
  DNS issue). Probing community mirrors of the same API; will default the
  module base URL to whatever verifies, env-overridable, and flag the
  host choice to agent-1.

### 2026-11-01 11:15 UTC — agent-ember
- STATUS: QUESTION (proceeding unless vetoed)
- TASK: T4
- FILES: (none yet)
- WHAT: Spec pivot needed: saavn.dev is dead (rajput-hemant/jiosaavn-api
  flagged "Project Discontinued" 2026-06, domain NXDOMAIN globally). I
  live-verified the NATIVE JioSaavn API instead:
  www.jiosaavn.com/api.php (__call=search.getResults / song.getDetails,
  _format=json&api_version=4&ctx=web6dot0) + DES-ECB decrypt of
  more_info.encrypted_media_url (key "38346591", PKCS7, base64 ct) →
  aac.saavncdn.com/..._96.mp4, _96→_320 swap gives 320kbps. Verified live:
  Believer → HTTP 200 audio/mp4, 8.5 MB / 204 s. saavn.rs will target the
  native API (base env-overridable) with a tolerant serde parse of THIS
  verified shape; everything else in #54 unchanged. agent-1: confirm or
  redirect.

### 2026-11-01 11:40 UTC — agent-1
- STATUS: WORKING — SUITE LOCK taken
- TASK: T3
- FILES: src-tauri/src/online/sources.rs, stream.rs, client.rs, mod.rs, README.md, ROADMAP.md
- WHAT: sources.rs implemented + integrated in resolve_with (cache → fast
  path → yt-dlp; yt-dlp availability no longer gates cache hits or the fast
  path). 142 tests green (11 new sources tests). Live findings: instance
  ecosystem degraded today (walls/dead backends — failover completes in
  6.2 s then falls back), yt-dlp extraction HUNG 60 s (reproduced #53 live).
  Now running all 8 browser suites; lock held until next entry.
