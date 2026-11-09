# agents.md — the Musico agent-team playbook

Musico Online is developed by a team of AI agents working **in parallel, in
separate chats that cannot see each other**, plus human review. This document
is the durable protocol that keeps that safe. The **live coordination bus** is
[`agentchat.md`](agentchat.md) — every agent must read it before touching any
code, and log to it while working.

The rules here exist because parallel agents break each other's work silently
when they improvise. If a rule blocks something you genuinely need, log a
`QUESTION` entry on the hub and keep moving on a non-conflicting part of your
task — don't work around it.

## The one-message-bus rule

Forked chats share nothing. `agentchat.md` is the **only** channel between
agents. Anything another agent (or the next session of your own agent) needs
to know — claims, progress, review requests, blockages, release notes — goes
in a log entry there.

- Append entries at the **bottom** of the Log section (newest last), one block
  per event:

  ```
  ### 2026-11-09 21:00 UTC — agent-<word>
  - STATUS: CLAIMED | WORKING | BLOCKED | QUESTION | CONFLICT | DONE | SUITE LOCK taken/released
  - TASK: T<n> / #<issue>
  - FILES: <paths touched>
  - WHAT: one or two concrete lines — what changed, what's next
  ```

- **Retention: only the 25 most recent entries stay.** After appending, trim
  the oldest from the top until 25 remain (dated log entries only — the
  format example in the Logging section does not count). Never delete a
  specific agent's entry otherwise; on merge conflicts, **union-merge** both
  sides' entries.
- Get timestamps with `date -u +"%Y-%m-%d %H:%M"`. File order is
  authoritative when clocks disagree.

## Joining

1. Pick a name: `agent-<word>`, any word **not already in the hub's Roster**
   (`agent-1` is reserved for the lead).
2. Append a CLAIMED entry naming your task.
3. Flip your task's status line to `IN PROGRESS — agent-<word>` in the Tasks
   section.
4. **One task per agent at a time.** Finish or hand it back before claiming
   another. The backlog section is not self-serve — the lead hands those out.

## Working rules (breaking these breaks another agent's work)

- **Landing rule, no exceptions:** issue → branch → PR → CI green →
  independent review → merge. Direct pushes to `main` are reverted on sight
  regardless of quality.
- **Work only in your own worktree** (`~/.cache/musico-worktrees/<name>` or a
  directory assigned to you). Inside it you may commit/push your own branch.
  The shared checkout belongs to the lead this round — never run git
  checkout/stash/reset there, never touch branches elsewhere.
- **`src-tauri/src/commands.rs` is append-only**: add commands, never reorder
  or reformat existing code. Same for `parse.rs` — it is the only
  response-shape-drift-sensitive file; keep it that way.
- **Edit the smallest region that does the job.** No whole-file rewrites, no
  drive-by refactors, no reformatting of untouched lines.
- **Frontend changed ⇒ bump the `?v=` stamp** in `frontend/index.html` (and
  `sw.js` `CACHE_NAME` when service-worker assets change).
- Two tasks need the same file region? Log a `CONFLICT` entry and wait for
  the lead — don't resolve it unilaterally.

## Gates before every push

- `cargo test --workspace` green (see `docs/testing.md` for the current count
  and the `gen_test_tone.sh` fixture step the engine e2e tests need).
- `cargo clippy` with **no NEW warnings** — pre-existing lints outside your
  diff are someone else's cleanup.
- The 9 Playwright suites (`scripts/browser_*_test.mjs`) for anything
  frontend-adjacent. They **serialize on port 8143**:
  1. Log `SUITE LOCK taken` on the hub.
  2. Serve the frontend tree and **curl-verify it before running** (stale
     servers from other agents are a recurring failure mode — kill by PID,
     never `pkill -f`).
  3. Run the suites; log `SUITE LOCK released` after the server is dead.
- A known flaky suite may fail once — rerun 3× per `docs/testing.md` before
  believing it.

## Device & release rules (Android)

- Android release APKs come **only** from the tag workflow (keystore secrets
  live on GitHub). Never build or hand over an unsigned APK — it will not
  install.
- `release.yml` supports `workflow_dispatch` for building a signed test APK
  from any branch (publish stays tag-gated). Install with
  `adb install -r <apk>`, launch/verify with the monkey + logcat recipe in
  `docs/development.md`.
- Reproduce on device before believing an emulator-only or desktop-only
  diagnosis; Android-specific gaps (no `$HOME`, no yt-dlp, private-storage
  paths — see `docs/data-storage.md`) have bitten this project repeatedly.

## Escalation

- **Blocked or unsure** → `QUESTION` entry; keep moving on non-conflicting
  work.
- **Review**: every PR gets an independent review (another agent or the
  lead). Verdicts are `approve` or `request-changes` with findings applied
  before merge — see the hub's history for what real review findings look
  like (capability gates silently no-op'ing, IPC arg-casing killing a whole
  feature — the kind of thing only a second reader catches).
- **Product decisions** (UI shape, scope, releases) belong to Aryan, relayed
  via the hub. Blocked-on-Aryan items say so in the backlog section.
